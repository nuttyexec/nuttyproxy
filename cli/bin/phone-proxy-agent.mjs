#!/usr/bin/env node
/**
 * Generic control plane for the Android Phone Proxy Agent protocol.
 *
 * It deliberately has no knowledge of a specific product: any service can run this process,
 * point a public WSS reverse proxy at it, and use the enrolled loopback port as
 * a standard HTTP CONNECT proxy.
 */
import crypto from "node:crypto";
import childProcess from "node:child_process";
import fs from "node:fs";
import http from "node:http";
import https from "node:https";
import net from "node:net";
import path from "node:path";
import process from "node:process";
import readline from "node:readline/promises";
import QRCode from "qrcode";
import qrTerminal from "qrcode-terminal";
import WebSocket, { WebSocketServer } from "ws";

const VERSION = 1;
const MAX_HEADER = 64 * 1024;
const MAX_PENDING = 1024 * 1024;
const HEARTBEAT_TIMEOUT = 4 * 60_000;
const MAX_WS_BUFFERED = 2 * 1024 * 1024;
const ENROLLMENT_RESERVATION_TIMEOUT = 60_000;
const FRAME_TO_PHONE = 1;
const FRAME_TO_GATEWAY = 2;
// This GitHub endpoint redirects to the APK asset on the newest published
// release.  The install QR is intentionally not tied to the CLI's own version:
// an operator may keep a stable gateway running while installing the current
// phone app.
const DEFAULT_INSTALL_URL = "https://github.com/nuttyexec/nuttyproxy/releases/latest/download/app-release.apk";
const DEFAULT_GATEWAY_PORT = 41082;
const DEFAULT_WSS_PATH = "/nutty-proxy";
const DEFAULT_IP_TLS_PORT = 8443;

function fail(message) { console.error(`phone-proxy-agent: ${message}`); process.exitCode = 1; }
function usage() {
  console.log(`
nuttyproxy <command>

  init      create a server configuration
  setup     configure the public WSS endpoint and server-wide daemon
  serve     run the local WSS gateway and loopback proxy listeners
  pair      create and print a one-time Android pairing QR
  installqr print the Nutty Proxy APK download QR
  service   install the one server-wide systemd daemon
  proxy     print a phone's local proxy URL
  agents    list, disable, enable, or revoke enrolled phones

Run 'nuttyproxy <command> --help' for command options.`);
}
function args(argv) {
  const values = { _: [] };
  for (let i = 0; i < argv.length; i += 1) {
    const token = argv[i];
    if (!token.startsWith("--")) { values._.push(token); continue; }
    const [key, inline] = token.slice(2).split("=", 2);
    if (inline !== undefined) values[key] = inline;
    else if (argv[i + 1] && !argv[i + 1].startsWith("--")) values[key] = argv[++i];
    else values[key] = true;
  }
  return values;
}
function defaultStateDir() {
  // Nutty Proxy is one server-wide daemon, not a project-local helper.  Its
  // configuration and enrollment records must therefore never depend on the
  // operator's current directory or home directory.
  if (process.env.NUTTYPROXY_STATE_DIR) return path.resolve(process.env.NUTTYPROXY_STATE_DIR);
  return "/var/lib/nuttyproxy";
}
function configPath(options) { return path.resolve(options.config || path.join(options["state-dir"] || defaultStateDir(), "config.json")); }
function readJson(file, fallback) { try { return JSON.parse(fs.readFileSync(file, "utf8")); } catch (error) { if (error.code === "ENOENT") return fallback; throw error; } }
function writeJson(file, value) {
  fs.mkdirSync(path.dirname(file), { recursive: true, mode: 0o700 });
  fs.chmodSync(path.dirname(file), 0o700);
  const temporary = `${file}.${process.pid}.tmp`;
  fs.writeFileSync(temporary, `${JSON.stringify(value, null, 2)}\n`, { mode: 0o600 });
  fs.renameSync(temporary, file);
}
function loadConfig(options) {
  const file = configPath(options);
  const config = readJson(file, null);
  if (!config) throw new Error(`configuration not found: ${file}; run init first`);
  if (config.version !== VERSION) throw new Error("unsupported configuration version");
  return { file, config };
}
function dataFile(configFile) { return path.join(path.dirname(configFile), "agents.json"); }
function loadData(file) { return readJson(file, { agents: [], enrollments: [] }); }
function saveData(file, data) { writeJson(file, data); }
function tokenHash(value) { return crypto.createHash("sha256").update(value).digest("hex"); }
function base64Url(value) { return Buffer.from(value).toString("base64url"); }
function validCertificatePin(value) {
  if (typeof value !== "string" || !value.startsWith("sha256/")) return false;
  const encoded = value.slice("sha256/".length);
  return /^[A-Za-z0-9+/]{43}=$/.test(encoded) && Buffer.from(encoded, "base64").length === 32;
}
function validPublicJwk(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  if (value.kty === "EC") return value.crv === "P-256" && typeof value.x === "string" && typeof value.y === "string";
  return value.kty === "OKP" && value.crv === "Ed25519" && typeof value.x === "string";
}
function deviceName(value) {
  if (typeof value !== "string") return "Phone";
  const normalized = value.replace(/[\u0000-\u001f\u007f]/g, "").trim().slice(0, 32);
  return normalized || "Phone";
}
function validateConfig(config) {
  const url = new URL(config.publicUrl);
  if (url.protocol !== "wss:" || !url.hostname || url.username || url.password || url.search || url.hash) throw new Error("public URL must be a clean wss:// URL");
  if (!validCertificatePin(config.certificatePin)) throw new Error("certificate pin must be sha256/<32-byte-SPKI-base64>");
  if (!isLoopback(config.gatewayHost) || !Number.isInteger(config.gatewayPort) || config.gatewayPort < 1 || config.gatewayPort > 65535 || typeof config.path !== "string" || !config.path.startsWith("/")) throw new Error("gateway listener must use loopback, a valid port, and a / path");
  if (url.pathname !== config.path) throw new Error("public URL path and gateway path must match");
}
function signaturePayload(agentId, challenge) { return Buffer.from(`phone-proxy-agent/v${VERSION}\n${agentId}\n${challenge}`); }
function sendJson(ws, value) { if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(value)); }
function proxyResponse(socket, status, message) { socket.end(`HTTP/1.1 ${status} ${message}\r\nConnection: close\r\nContent-Length: 0\r\n\r\n`); }
function isLoopback(host) { return host === "127.0.0.1" || host === "::1" || host === "localhost"; }
function parseAuthority(value, defaultPort) {
  if (value.startsWith("[")) {
    const closing = value.indexOf("]");
    const port = Number(value.slice(closing + 1).replace(/^:/, "") || defaultPort);
    return closing > 0 && Number.isInteger(port) && port > 0 && port <= 65535 ? { host: value.slice(1, closing), port } : null;
  }
  const last = value.lastIndexOf(":");
  if (last <= 0 || value.indexOf(":") !== last) return { host: value, port: defaultPort };
  const port = Number(value.slice(last + 1));
  return Number.isInteger(port) && port > 0 && port <= 65535 ? { host: value.slice(0, last), port } : null;
}
function parseProxyHeader(buffer) {
  const marker = buffer.indexOf("\r\n\r\n");
  if (marker < 0) return null;
  const header = buffer.subarray(0, marker + 4).toString("latin1");
  const [first, ...headers] = header.slice(0, -4).split("\r\n");
  const match = first.match(/^([A-Z]+)\s+(\S+)\s+HTTP\/1\.[01]$/);
  if (!match) return null;
  const [, method, target] = match;
  const remainder = buffer.subarray(marker + 4);
  if (method === "CONNECT") {
    const authority = parseAuthority(target, 443);
    return authority && { ...authority, method, mode: "connect", initialPayload: remainder };
  }
  let url;
  try { url = new URL(target); } catch { return null; }
  if (url.protocol !== "http:" || !url.hostname) return null;
  const port = Number(url.port || 80);
  if (!Number.isInteger(port) || port < 1 || port > 65535) return null;
  // Proxy credentials are hop-by-hop metadata and must never be delivered to
  // the target origin when this gateway forwards plain HTTP traffic.
  const forwardHeaders = headers.filter((line) => !/^proxy-(?:connection|authorization|authenticate)\s*:/i.test(line));
  const targetPath = `${url.pathname || "/"}${url.search}`;
  const rewritten = Buffer.from(`${method} ${targetPath} HTTP/1.1\r\n${forwardHeaders.join("\r\n")}\r\n\r\n`, "latin1");
  return { host: url.hostname, port, method, mode: "http", initialPayload: Buffer.concat([rewritten, remainder]) };
}

class ConnectedAgent {
  constructor(agent, ws, onStop) {
    this.agent = agent; this.ws = ws; this.onStop = onStop; this.streams = new Map(); this.nextId = 1; this.listener = null; this.lastSeen = Date.now();
  }
  async start() {
    if (!isLoopback(this.agent.listenHost)) throw new Error("proxy listener must use a loopback host");
    this.listener = net.createServer((socket) => this.accept(socket));
    await new Promise((resolve, reject) => { this.listener.once("error", reject); this.listener.listen(this.agent.listenPort, this.agent.listenHost, resolve); });
  }
  stop() {
    if (this.stopped) return;
    this.stopped = true;
    this.listener?.close(); this.listener = null;
    for (const stream of this.streams.values()) stream.socket.destroy();
    this.streams.clear(); this.onStop(this.agent.agentId, this);
  }
  message(message) {
    this.lastSeen = Date.now();
    if (message.type === "heartbeat") return sendJson(this.ws, { type: "heartbeat_ack" });
    if (message.type === "opened") return this.open(message.streamId);
    if (message.type === "closed") return this.close(message.streamId);
    if (message.type === "stream_error") return this.fail(message.streamId, message.message || "phone socket error");
  }
  frame(frame) { this.lastSeen = Date.now(); if (frame.length < 5 || frame.readUInt8(0) !== FRAME_TO_GATEWAY) return; const stream = this.streams.get(frame.readUInt32BE(1)); if (stream && !stream.socket.destroyed) stream.socket.write(frame.subarray(5)); }
  accept(socket) {
    socket.setNoDelay(true); socket.setTimeout(15_000, () => socket.destroy()); let received = Buffer.alloc(0);
    const header = (chunk) => {
      received = Buffer.concat([received, chunk]);
      if (received.length > MAX_HEADER) return proxyResponse(socket, 431, "Request Header Fields Too Large");
      if (!received.includes("\r\n\r\n")) return;
      socket.off("data", header); const request = parseProxyHeader(received);
      if (!request) return proxyResponse(socket, 400, "Bad Request");
      this.createStream(socket, request);
    };
    socket.on("data", header); socket.once("error", () => undefined);
  }
  createStream(socket, request) {
    const stream = { id: this.nextId++, socket, ...request, opened: false, pending: [], pendingBytes: 0 };
    this.streams.set(stream.id, stream);
    socket.on("data", (chunk) => this.forward(stream, chunk)); socket.once("close", () => this.closeFromGateway(stream)); socket.once("error", () => this.closeFromGateway(stream));
    sendJson(this.ws, { type: "open", streamId: stream.id, host: request.host, port: request.port, method: request.method });
  }
  open(id) {
    const stream = this.streams.get(id);
    if (!stream || stream.opened) return;
    stream.opened = true;
    stream.socket.setTimeout(0);
    if (stream.mode === "connect") stream.socket.write("HTTP/1.1 200 Connection Established\r\n\r\n");
    if (stream.initialPayload.length && !this.sendFrame(stream.id, stream.initialPayload)) return;
    for (const chunk of stream.pending) if (!this.sendFrame(stream.id, chunk)) return;
    stream.pending = []; stream.pendingBytes = 0;
  }
  forward(stream, chunk) { if (stream.opened) return this.sendFrame(stream.id, chunk); stream.pendingBytes += chunk.length; if (stream.pendingBytes > MAX_PENDING) return this.fail(stream.id, "phone did not open stream"); stream.pending.push(chunk); }
  sendFrame(id, payload) {
    if (this.ws.readyState !== WebSocket.OPEN || this.ws.bufferedAmount + 5 + payload.length > MAX_WS_BUFFERED) {
      this.fail(id, "gateway tunnel backpressure");
      return false;
    }
    const frame = Buffer.allocUnsafe(5 + payload.length);
    frame.writeUInt8(FRAME_TO_PHONE, 0); frame.writeUInt32BE(id, 1); payload.copy(frame, 5);
    this.ws.send(frame);
    return true;
  }
  closeFromGateway(stream) { if (!this.streams.delete(stream.id)) return; sendJson(this.ws, { type: "close", streamId: stream.id }); }
  close(id) { const stream = this.streams.get(id); if (!stream) return; this.streams.delete(id); stream.socket.end(); }
  fail(id, detail) { const stream = this.streams.get(id); if (!stream) return; this.streams.delete(id); if (!stream.opened) proxyResponse(stream.socket, 502, "Bad Gateway"); else stream.socket.destroy(); sendJson(this.ws, { type: "close", streamId: id }); console.warn(`stream ${this.agent.agentId}/${id}: ${detail}`); }
}

function verify(agent, signature, challenge) {
  try {
    const key = crypto.createPublicKey({ key: agent.publicKeyJwk, format: "jwk" });
    return crypto.verify(agent.publicKeyJwk.kty === "EC" ? "sha256" : null, signaturePayload(agent.agentId, challenge), key, Buffer.from(signature, "base64url"));
  } catch {
    return false;
  }
}
function reserveEnrollment(stateFile, hello) {
  const data = loadData(stateFile);
  const existing = data.agents.find((entry) => entry.agentId === hello.agentId);
  if (existing) return { data, agent: existing, enrollment: null };
  const now = Date.now();
  const enrollment = typeof hello.enrollmentToken === "string" && validPublicJwk(hello.publicKeyJwk)
    ? data.enrollments.find((entry) => entry.tokenHash === tokenHash(hello.enrollmentToken) && entry.agentId === hello.agentId && entry.expiresAt > now && !entry.claimedAt && (!entry.reservedAt || entry.reservedAt + ENROLLMENT_RESERVATION_TIMEOUT < now))
    : null;
  if (!enrollment) return null;
  // Reserve before issuing a challenge. Without this, two holders of one QR
  // token could authenticate concurrently and race to overwrite agents.json.
  enrollment.claimNonce = base64Url(crypto.randomBytes(32));
  enrollment.reservedAt = now;
  saveData(stateFile, data);
  return { data, agent: null, enrollment };
}
function consumeEnrollment(stateFile, enrollment, record) {
  const data = loadData(stateFile);
  const index = data.enrollments.findIndex((entry) => entry.tokenHash === enrollment.tokenHash && entry.claimNonce === enrollment.claimNonce && entry.agentId === record.agentId && !entry.claimedAt);
  if (index < 0 || data.agents.some((entry) => entry.agentId === record.agentId)) return false;
  data.enrollments.splice(index, 1);
  data.agents.push(record);
  saveData(stateFile, data);
  return true;
}
function portInUse(data, host, port) {
  return [...data.agents, ...data.enrollments.filter((entry) => !entry.claimedAt && entry.expiresAt > Date.now())]
    .some((entry) => entry.listenHost === host && entry.listenPort === port);
}
async function portAvailable(host, port) {
  const probe = net.createServer();
  try {
    await new Promise((resolve, reject) => { probe.once("error", reject); probe.listen(port, host, resolve); });
    return true;
  } catch {
    return false;
  } finally {
    await new Promise((resolve) => probe.close(() => resolve()));
  }
}
async function chooseProxyPort(data, host) {
  for (let attempts = 0; attempts < 128; attempts += 1) {
    const port = 42000 + crypto.randomInt(1000);
    if (!portInUse(data, host, port) && await portAvailable(host, port)) return port;
  }
  throw new Error("could not find an unused loopback proxy port");
}
function runGateway(configFile, config) {
  validateConfig(config);
  const stateFile = dataFile(configFile); const active = new Map();
  const server = http.createServer((_, response) => response.writeHead(404).end()); const wss = new WebSocketServer({ noServer: true, maxPayload: 2 * 1024 * 1024 });
  server.on("upgrade", (request, socket, head) => { if (request.url?.split("?")[0] !== config.path) return socket.destroy(); wss.handleUpgrade(request, socket, head, (ws) => accept(ws)); });
  function accept(ws) {
    let connected = null; let pending = null;
    ws.once("message", (raw, binary) => {
      if (binary) return ws.close(1003, "hello must be JSON"); let hello;
      try { hello = JSON.parse(raw.toString()); } catch { return ws.close(1003, "invalid hello"); }
      if (hello?.type !== "hello" || hello.version !== VERSION || typeof hello.agentId !== "string") return ws.close(1008, "invalid hello");
      const reserved = reserveEnrollment(stateFile, hello);
      if (!reserved) return ws.close(1008, "enrollment required");
      const { agent, enrollment } = reserved;
      const record = agent || { agentId: enrollment.agentId, deviceName: deviceName(hello.deviceName), serverName: enrollment.serverName, listenHost: enrollment.listenHost, listenPort: enrollment.listenPort, enabled: true, publicKeyJwk: hello.publicKeyJwk };
      if (!record.enabled) return ws.close(1008, "agent disabled");
      pending = { enrollment, record, challenge: base64Url(crypto.randomBytes(32)) }; sendJson(ws, { type: "challenge", version: VERSION, challenge: pending.challenge });
      // `ws` can invoke listeners added during the hello emission for that same
      // message. Defer registration so hello is never mistaken for authenticate.
      queueMicrotask(() => ws.on("message", async (raw, binary) => {
      if (binary) return connected?.frame(Buffer.from(raw)); let message;
      try { message = JSON.parse(raw.toString()); } catch { return ws.close(1003, "invalid JSON"); }
      if (!connected) {
        if (!pending || message.type !== "authenticate" || typeof message.signature !== "string" || !verify(pending.record, message.signature, pending.challenge)) {
          return ws.close(1008, "signature rejected");
        }
        if (pending.enrollment && !consumeEnrollment(stateFile, pending.enrollment, pending.record)) return ws.close(1008, "enrollment already consumed");
        active.get(pending.record.agentId)?.stop(); connected = new ConnectedAgent(pending.record, ws, (id, instance) => { if (active.get(id) === instance) active.delete(id); });
        try { await connected.start(); } catch (error) { connected = null; sendJson(ws, { type: "error", code: "listen_failed" }); return ws.close(1011, String(error)); }
        active.set(pending.record.agentId, connected); return sendJson(ws, { type: "ready", agentId: pending.record.agentId, heartbeatIntervalMs: 90_000 });
      }
      connected.message(message);
      }));
    });
    ws.once("close", () => connected?.stop()); ws.once("error", () => connected?.stop());
  }
  let reconcileTimer = null;
  const reconcileAccess = () => {
    const agents = loadData(stateFile).agents;
    for (const connection of active.values()) {
      const current = agents.find((agent) => agent.agentId === connection.agent.agentId);
      if (!current || !current.enabled) {
        sendJson(connection.ws, { type: "error", code: current ? "agent_disabled" : "agent_revoked" });
        connection.stop();
        connection.ws.close(1008, current ? "agent disabled" : "agent revoked");
      }
    }
  };
  // `writeJson` atomically replaces the file, so watch the directory rather
  // than the file inode. This makes disable/revoke effective for live tunnels.
  const watcher = fs.watch(path.dirname(stateFile), (event, changed) => {
    if (changed?.toString() !== path.basename(stateFile)) return;
    if (reconcileTimer) clearTimeout(reconcileTimer);
    reconcileTimer = setTimeout(reconcileAccess, 25);
  });
  const timer = setInterval(() => { for (const connection of active.values()) if (Date.now() - connection.lastSeen > HEARTBEAT_TIMEOUT) connection.ws.terminate(); }, 30_000);
  server.listen(config.gatewayPort, config.gatewayHost, () => {
    const address = server.address();
    console.log(`Phone Proxy Agent gateway listening on ${config.gatewayHost}:${address.port}${config.path}`);
  });
  const stop = () => { clearInterval(timer); watcher.close(); if (reconcileTimer) clearTimeout(reconcileTimer); for (const connection of active.values()) connection.stop(); server.close(() => process.exit(0)); };
  process.on("SIGINT", stop); process.on("SIGTERM", stop);
}

function run(command, commandArgs, options = {}) {
  const result = childProcess.spawnSync(command, commandArgs, {
    encoding: "utf8",
    stdio: options.stdio || "pipe",
  });
  if (result.error) throw new Error(`${command}: ${result.error.message}`);
  if (result.status !== 0) {
    const detail = [result.stderr, result.stdout].filter(Boolean).join("\n").trim();
    throw new Error(`${command} ${commandArgs.join(" ")} failed${detail ? `: ${detail}` : ""}`);
  }
  return result.stdout || "";
}
function requireRootSetup() {
  if (process.platform !== "linux" || typeof process.getuid !== "function" || process.getuid() !== 0) {
    throw new Error("run setup with sudo: sudo nuttyproxy setup");
  }
  if (!process.env.SUDO_USER || !process.env.SUDO_UID || !process.env.SUDO_GID) {
    throw new Error("run setup through sudo from the account that will own the daemon");
  }
  if (!process.env.SUDO_USER.match(/^[a-z_][a-z0-9_-]*[$]?$/i) || !Number.isInteger(Number(process.env.SUDO_UID)) || !Number.isInteger(Number(process.env.SUDO_GID))) {
    throw new Error("the sudo daemon account is invalid");
  }
  return { user: process.env.SUDO_USER, uid: Number(process.env.SUDO_UID), gid: Number(process.env.SUDO_GID) };
}
function cleanWssPath(value) {
  const candidate = (value || DEFAULT_WSS_PATH).trim();
  if (!candidate.startsWith("/") || candidate.includes("?") || candidate.includes("#") || candidate.includes("//") || !/^\/[A-Za-z0-9._~!$&'()*+,;=:@%/-]*$/.test(candidate)) {
    throw new Error("WSS path must be a clean absolute path, for example /nutty-proxy");
  }
  return candidate;
}
function isPublicAddress(value) {
  const version = net.isIP(value);
  if (version === 4) {
    const octets = value.split(".").map(Number);
    return !(
      octets[0] === 0 || octets[0] === 10 || octets[0] === 127 || octets[0] >= 224 ||
      (octets[0] === 100 && octets[1] >= 64 && octets[1] <= 127) ||
      (octets[0] === 169 && octets[1] === 254) ||
      (octets[0] === 172 && octets[1] >= 16 && octets[1] <= 31) ||
      (octets[0] === 192 && octets[1] === 168)
    );
  }
  if (version === 6) return value !== "::1" && !/^f[cd]/i.test(value) && !/^fe[89ab]/i.test(value);
  return false;
}
function validHostName(value) {
  return typeof value === "string" && value.length <= 253 && /^(?=.{1,253}$)(?!-)(?:[A-Za-z0-9-]{1,63}\.)+[A-Za-z]{2,63}$/.test(value);
}
function publicIp() {
  return new Promise((resolve) => {
    const request = https.get("https://api.ipify.org", { timeout: 4_000 }, (response) => {
      let body = "";
      response.setEncoding("utf8");
      response.on("data", (chunk) => { body += chunk; });
      response.on("end", () => resolve(isPublicAddress(body.trim()) ? body.trim() : null));
    });
    request.once("timeout", () => request.destroy());
    request.once("error", () => resolve(null));
  });
}
function uncommented(source) {
  let output = ""; let quote = null; let comment = false;
  for (let index = 0; index < source.length; index += 1) {
    const char = source[index];
    if (comment) { output += char === "\n" ? "\n" : " "; if (char === "\n") comment = false; continue; }
    if (!quote && char === "#") { output += " "; comment = true; continue; }
    if (char === "\\" && quote) { output += `${char}${source[++index] || ""}`; continue; }
    if (char === "\"" || char === "'") quote = quote === char ? null : (quote || char);
    output += char;
  }
  return output;
}
function serverBlocks(source) {
  const clean = uncommented(source); const blocks = []; const pattern = /\bserver\s*\{/g;
  for (let match; (match = pattern.exec(clean));) {
    const opening = clean.indexOf("{", match.index); let depth = 0; let closing = -1;
    for (let index = opening; index < clean.length; index += 1) {
      if (clean[index] === "{") depth += 1;
      if (clean[index] === "}" && --depth === 0) { closing = index; break; }
    }
    if (closing >= 0) { blocks.push({ start: match.index, end: closing + 1, body: clean.slice(match.index, closing + 1) }); pattern.lastIndex = closing + 1; }
  }
  return blocks;
}
function nginxConfigFiles() {
  const output = run("nginx", ["-T"]); const files = new Map();
  const marker = /^# configuration file (.+):$/gm; const matches = [...output.matchAll(marker)];
  for (let index = 0; index < matches.length; index += 1) {
    const file = matches[index][1]; const start = matches[index].index + matches[index][0].length + 1;
    const end = index + 1 < matches.length ? matches[index + 1].index : output.length;
    if (fs.existsSync(file)) files.set(fs.realpathSync(file), fs.readFileSync(file, "utf8"));
  }
  return files;
}
function findDomainServer(host) {
  for (const [file, source] of nginxConfigFiles()) {
    for (const block of serverBlocks(source)) {
      const names = [...block.body.matchAll(/\bserver_name\s+([^;]+);/g)].flatMap((match) => match[1].trim().split(/\s+/));
      if (!names.includes(host)) continue;
      const cert = block.body.match(/\bssl_certificate\s+([^;\s]+)\s*;/)?.[1];
      const key = block.body.match(/\bssl_certificate_key\s+([^;\s]+)\s*;/)?.[1];
      const listensSsl = /\blisten\s+[^;]*\bssl\b[^;]*;/.test(block.body);
      if (cert && key && listensSsl) return { file, source, block, certificate: cert, key };
    }
  }
  return null;
}
function domainCandidates() {
  const names = new Set();
  for (const [, source] of nginxConfigFiles()) {
    for (const block of serverBlocks(source)) {
      if (!/\blisten\s+[^;]*\bssl\b[^;]*;/.test(block.body)) continue;
      for (const match of block.body.matchAll(/\bserver_name\s+([^;]+);/g)) {
        for (const name of match[1].trim().split(/\s+/)) if (validHostName(name)) names.add(name.toLowerCase());
      }
    }
  }
  return [...names].sort();
}
function nginxLocation(pathValue) {
  return `\n  # Nutty Proxy managed WSS endpoint. Pairing and device-key authentication\n  # happen in the local gateway; this location does not expose a proxy port.\n  location = ${pathValue} {\n    limit_req zone=nuttyproxy_handshake burst=20 nodelay;\n    limit_conn nuttyproxy_client 4;\n    proxy_pass http://127.0.0.1:${DEFAULT_GATEWAY_PORT};\n    proxy_http_version 1.1;\n    proxy_set_header Upgrade $http_upgrade;\n    proxy_set_header Connection "upgrade";\n    proxy_set_header Host $host;\n    proxy_read_timeout 3600s;\n    proxy_send_timeout 3600s;\n    proxy_buffering off;\n  }\n`;
}
function writeManaged(file, content, mode = 0o644) {
  const backup = `${file}.nuttyproxy-backup-${Date.now()}`;
  if (fs.existsSync(file)) fs.copyFileSync(file, backup);
  try {
    fs.writeFileSync(file, content, { mode });
    run("nginx", ["-t"]);
    run("systemctl", ["reload", "nginx"]);
  } catch (error) {
    if (fs.existsSync(backup)) fs.copyFileSync(backup, file); else fs.rmSync(file, { force: true });
    try { run("nginx", ["-t"]); run("systemctl", ["reload", "nginx"]); } catch { /* preserve the original failure */ }
    throw error;
  }
  return backup;
}
function ensureNginxLimits() {
  const file = "/etc/nginx/conf.d/nuttyproxy-rate-limit.conf";
  const content = "# Managed by Nutty Proxy. Limits apply only to its public WSS upgrade.\nlimit_req_zone $binary_remote_addr zone=nuttyproxy_handshake:10m rate=10r/m;\nlimit_conn_zone $binary_remote_addr zone=nuttyproxy_client:10m;\n";
  if (fs.existsSync(file) && fs.readFileSync(file, "utf8") === content) return;
  writeManaged(file, content);
}
function configureDomainEndpoint(host, pathValue) {
  const target = findDomainServer(host);
  if (!target) throw new Error(`no TLS Nginx server for ${host} was found; choose a configured domain or use its public IP`);
  if (!fs.existsSync(target.certificate) || !fs.existsSync(target.key)) throw new Error(`the Nginx certificate files for ${host} are not readable`);
  const locationPattern = new RegExp(`\\blocation\\s*=\\s*${pathValue.replace(/[.*+?^${}()|[\\]\\\\]/g, "\\\\$&")}\\s*\\{`);
  if (!locationPattern.test(target.block.body)) {
    const insertion = target.source.slice(0, target.block.end - 1) + nginxLocation(pathValue) + target.source.slice(target.block.end - 1);
    writeManaged(target.file, insertion);
  }
  return { certificate: target.certificate, url: `wss://${host}${pathValue}` };
}
function certificatePin(certificateFile) {
  const certificate = new crypto.X509Certificate(fs.readFileSync(certificateFile));
  const der = certificate.publicKey.export({ type: "spki", format: "der" });
  return `sha256/${crypto.createHash("sha256").update(der).digest("base64")}`;
}
function openUfwPort(port) {
  const status = childProcess.spawnSync("ufw", ["status"], { encoding: "utf8" });
  if (status.error?.code === "ENOENT" || status.status !== 0 || !/^Status:\s+active/im.test(status.stdout || "")) return;
  run("ufw", ["allow", `${port}/tcp`]);
  console.log(`Allowed TCP ${port} through UFW.`);
}
function parseVersion(value) {
  const match = value.match(/(\d+)\.(\d+)\.(\d+)/);
  return match ? match.slice(1).map(Number) : null;
}
function atLeastVersion(actual, expected) {
  if (!actual) return false;
  for (let index = 0; index < expected.length; index += 1) {
    if (actual[index] > expected[index]) return true;
    if (actual[index] < expected[index]) return false;
  }
  return true;
}
async function ask(terminal, prompt, defaultValue = "") {
  const suffix = defaultValue ? ` [${defaultValue}]` : "";
  const answer = (await terminal.question(`${prompt}${suffix}: `)).trim();
  return answer || defaultValue;
}
function installService(options = {}) {
  const { user } = requireRootSetup();
  const { file } = loadConfig(options); const stateDir = path.dirname(file);
  const entrypoint = fs.realpathSync(process.argv[1]);
  if ([process.execPath, entrypoint, stateDir].some((value) => /\s/.test(value))) throw new Error("service paths cannot contain whitespace");
  const unit = `[Unit]\nDescription=Nutty Proxy gateway\nWants=network-online.target\nAfter=network-online.target\n\n[Service]\nType=simple\nUser=${user}\nGroup=${user}\nWorkingDirectory=${path.dirname(entrypoint)}\nEnvironment=NUTTYPROXY_STATE_DIR=${stateDir}\nExecStart=${process.execPath} ${entrypoint} serve\nRestart=on-failure\nRestartSec=3s\nUMask=0077\nNoNewPrivileges=true\nPrivateTmp=true\n\n[Install]\nWantedBy=multi-user.target\n`;
  fs.writeFileSync("/etc/systemd/system/nuttyproxy.service", unit, { mode: 0o644 });
  run("systemctl", ["daemon-reload"]);
  run("systemctl", ["enable", "nuttyproxy.service"]);
  run("systemctl", ["restart", "nuttyproxy.service"]);
  console.log(`Nutty Proxy is running as ${user} with state ${stateDir}`);
}

function ipCertificateName(ip) { return `nuttyproxy-ip-${ip.replace(/[^A-Za-z0-9]/g, "-")}`; }
async function configureIpEndpoint(ip, pathValue, terminal) {
  const rawVersion = run("certbot", ["--version"]);
  const version = parseVersion(rawVersion);
  if (!atLeastVersion(version, [5, 3, 0])) {
    throw new Error(`public-IP TLS needs Certbot 5.3 or newer (found ${rawVersion.trim() || "unknown"}). Use an existing domain now, or upgrade Certbot before choosing the IP route.`);
  }
  const email = await ask(terminal, "Let's Encrypt renewal email");
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) throw new Error("a valid renewal email is required for a public-IP certificate");
  const portText = await ask(terminal, "Public HTTPS port", String(DEFAULT_IP_TLS_PORT));
  const port = Number(portText);
  if (!Number.isInteger(port) || port < 1 || port > 65535 || port === 80) throw new Error("choose a valid HTTPS port other than 80");
  if (!await portAvailable(net.isIP(ip) === 6 ? "::1" : "127.0.0.1", port)) throw new Error(`local port ${port} is already in use`);
  const confirmation = (await ask(terminal, "Certbot will briefly stop Nginx to validate the IP certificate. Continue", "Y")).toLowerCase();
  if (confirmation !== "y" && confirmation !== "yes") throw new Error("setup cancelled");
  const name = ipCertificateName(ip);
  run("certbot", [
    "certonly", "--standalone", "--non-interactive", "--agree-tos", "--preferred-profile", "shortlived",
    "--ip-address", ip, "--email", email, "--cert-name", name, "--reuse-key",
    "--pre-hook", "systemctl stop nginx", "--post-hook", "systemctl start nginx", "--deploy-hook", "systemctl reload nginx",
  ], { stdio: "inherit" });
  const certificate = `/etc/letsencrypt/live/${name}/fullchain.pem`;
  const key = `/etc/letsencrypt/live/${name}/privkey.pem`;
  if (!fs.existsSync(certificate) || !fs.existsSync(key)) throw new Error("Certbot did not create the expected IP certificate files");
  ensureNginxLimits();
  const file = "/etc/nginx/conf.d/nuttyproxy-ip.conf";
  const listen = net.isIP(ip) === 6 ? `listen [::]:${port} ssl;` : `listen ${port} ssl;`;
  const config = `# Managed by Nutty Proxy. This separate port avoids changing another site's TLS default.\nserver {\n  ${listen}\n  server_name _;\n  ssl_certificate ${certificate};\n  ssl_certificate_key ${key};${nginxLocation(pathValue)}}\n`;
  writeManaged(file, config);
  openUfwPort(port);
  return { certificate, url: `wss://${net.isIP(ip) === 6 ? `[${ip}]` : ip}:${port}${pathValue}` };
}
async function commandSetup(options) {
  if (options.help) return console.log("setup\n\nRun with sudo. It configures an existing Nginx TLS domain (recommended), or a public IP with a short-lived Let's Encrypt IP certificate.");
  const owner = requireRootSetup();
  if (!fs.existsSync("/usr/sbin/nginx") && !fs.existsSync("/usr/bin/nginx")) throw new Error("Nginx is required for setup; install and configure Nginx first");
  const stateFile = configPath(options); const existing = readJson(stateFile, null);
  if (existing) {
    const data = loadData(dataFile(stateFile));
    if (data.agents.length) throw new Error("phones are already paired. Do not change this endpoint in place; keep it or revoke/re-pair those phones first.");
  }
  const candidates = domainCandidates(); const detectedIp = await publicIp();
  let previousHost = "";
  try { previousHost = new URL(existing?.publicUrl || "").hostname; } catch { /* no previous configuration */ }
  const defaultAddress = candidates.includes(previousHost) ? previousHost : (candidates[0] || detectedIp || "");
  if (!process.stdin.isTTY && !options.address) throw new Error("setup needs a terminal (or the advanced --address option)");
  const terminal = process.stdin.isTTY ? readline.createInterface({ input: process.stdin, output: process.stdout }) : null;
  try {
    if (candidates.length) console.log(`Detected Nginx TLS domain${candidates.length > 1 ? "s" : ""}: ${candidates.join(", ")}`);
    else if (detectedIp) console.log(`Detected public IP: ${detectedIp}`);
    const address = (options.address || await ask(terminal, "Public address (configured domain recommended, or public IP)", defaultAddress)).toLowerCase();
    if (!validHostName(address) && !isPublicAddress(address)) throw new Error("enter a configured public domain or a public IP address");
    const pathValue = cleanWssPath(options.path || (terminal ? await ask(terminal, "WSS path", DEFAULT_WSS_PATH) : DEFAULT_WSS_PATH));
    let endpoint;
    if (validHostName(address)) {
      ensureNginxLimits();
      endpoint = configureDomainEndpoint(address, pathValue);
    } else {
      endpoint = await configureIpEndpoint(address, pathValue, terminal);
    }
    const config = {
      version: VERSION,
      publicUrl: endpoint.url,
      serverName: address,
      certificatePin: certificatePin(endpoint.certificate),
      gatewayHost: "127.0.0.1",
      gatewayPort: DEFAULT_GATEWAY_PORT,
      path: pathValue,
      createdAt: existing?.createdAt || new Date().toISOString(),
    };
    validateConfig(config);
    fs.mkdirSync(path.dirname(stateFile), { recursive: true, mode: 0o700 });
    fs.chownSync(path.dirname(stateFile), owner.uid, owner.gid); fs.chmodSync(path.dirname(stateFile), 0o700);
    writeJson(stateFile, config); fs.chownSync(stateFile, owner.uid, owner.gid);
    // A changed endpoint invalidates QR payloads that have not been scanned.
    // Keep this explicit rather than leaving a stale enrollment that can only
    // fail later at a different WSS path.
    const agentStateFile = dataFile(stateFile); const data = loadData(agentStateFile);
    if (data.enrollments.length) {
      saveData(agentStateFile, { ...data, enrollments: [] });
      fs.chownSync(agentStateFile, owner.uid, owner.gid);
    }
    installService(options);
    console.log(`\nReady. Public endpoint: ${config.publicUrl}\nNext: nuttyproxy installqr, then nuttyproxy pair`);
  } finally { terminal?.close(); }
}

async function commandInit(options) {
  if (options.help) return console.log("init [--public-url wss://proxy.example.com/nutty-proxy --certificate-pin sha256/...]");
  let publicUrlValue = options["public-url"];
  let certificatePin = options["certificate-pin"];
  if (!publicUrlValue || !certificatePin) {
    if (!process.stdin.isTTY) throw new Error("--public-url and --certificate-pin are required without a terminal");
    const terminal = readline.createInterface({ input: process.stdin, output: process.stdout });
    try {
      publicUrlValue ||= await terminal.question("Public WSS URL: ");
      certificatePin ||= await terminal.question("TLS SPKI pin (sha256/...): ");
    } finally { terminal.close(); }
  }
  if (!validCertificatePin(certificatePin)) throw new Error("certificate pin must be sha256/<32-byte-SPKI-base64>");
  const publicUrl = new URL(publicUrlValue); if (publicUrl.protocol !== "wss:" || !publicUrl.hostname || publicUrl.username || publicUrl.password || publicUrl.search || publicUrl.hash) throw new Error("public URL must be a clean wss:// URL");
  const file = configPath(options); const config = { version: VERSION, publicUrl: publicUrl.toString(), serverName: options["server-name"] || publicUrl.hostname, certificatePin, gatewayHost: options["gateway-host"] || "127.0.0.1", gatewayPort: Number(options["gateway-port"] || DEFAULT_GATEWAY_PORT), path: options.path || publicUrl.pathname, createdAt: new Date().toISOString() };
  validateConfig(config);
  writeJson(file, config); console.log(`Wrote ${file}`);
}
async function commandPair(options) {
  if (options.help) return console.log("pair [--json] [--qr-file pairing.png] [--expires-minutes 10 --agent-id ID --listen-port PORT --listen-host 127.0.0.1]");
  const { file, config } = loadConfig(options); const minutes = Number(options["expires-minutes"] || 10);
  const listenHost = options["listen-host"] || "127.0.0.1";
  if (!isLoopback(listenHost) || !Number.isFinite(minutes) || minutes < 1 || minutes > 60) throw new Error("invalid loopback host or expiry");
  const data = loadData(dataFile(file));
  const agentId = options["agent-id"] || `agent_${base64Url(crypto.randomBytes(12))}`;
  const port = options["listen-port"] === undefined ? await chooseProxyPort(data, listenHost) : Number(options["listen-port"]);
  const serverName = config.serverName || new URL(config.publicUrl).hostname;
  if (!agentId.match(/^[A-Za-z0-9][A-Za-z0-9_-]{1,63}$/) || !Number.isInteger(port) || port < 1024 || port > 65535) throw new Error("invalid agent id or loopback proxy port");
  if (data.agents.some((agent) => agent.agentId === agentId) || data.enrollments.some((entry) => entry.agentId === agentId && !entry.claimedAt && entry.expiresAt > Date.now())) throw new Error("agent id already exists or has an active pairing");
  if (portInUse(data, listenHost, port) || !await portAvailable(listenHost, port)) throw new Error("loopback proxy port is already assigned");
  const token = base64Url(crypto.randomBytes(32)); const expiresAt = Date.now() + minutes * 60_000;
  data.enrollments = data.enrollments.filter((entry) => entry.expiresAt > Date.now() && !entry.claimedAt); data.enrollments.push({ tokenHash: tokenHash(token), agentId, serverName, listenHost, listenPort: port, expiresAt, claimedAt: null, reservedAt: null, claimNonce: null }); saveData(dataFile(file), data);
  const payload = { version: VERSION, gatewayUrl: config.publicUrl, certificatePin: config.certificatePin, agentId, serverName, enrollmentToken: token, expiresAt: new Date(expiresAt).toISOString() };
  const serialized = JSON.stringify(payload);
  if (typeof options["qr-file"] === "string") {
    const output = path.resolve(options["qr-file"]);
    const png = await QRCode.toBuffer(serialized, { errorCorrectionLevel: "M", margin: 2, width: 768 });
    fs.writeFileSync(output, png, { mode: 0o600 });
    fs.chmodSync(output, 0o600);
    console.error(`Pairing QR written to ${output}; it expires at ${payload.expiresAt}`);
  }
  if (options.qr || !options.json) {
    // QR goes to stdout by default for direct terminal scanning. `--json`
    // additionally prints the machine-readable payload on stderr for a
    // controlled handoff; use --json alone for JSON-only automation.
    qrTerminal.generate(serialized, { small: true }, (qr) => process.stdout.write(qr));
    console.error(`Pairing QR ready; the phone chooses its name. Proxy: http://${listenHost}:${port}. Expires: ${payload.expiresAt}`);
    if (options.json) console.error(JSON.stringify(payload, null, 2));
  } else {
    console.log(JSON.stringify(payload, null, 2));
  }
}
function commandInstallQr(options) {
  if (options.help) return console.log("installqr [--url https://example.com/app-release.apk]");
  const url = options.url || DEFAULT_INSTALL_URL;
  let parsed;
  try { parsed = new URL(url); } catch { throw new Error("--url must be an absolute https:// URL"); }
  if (parsed.protocol !== "https:" || !parsed.hostname || parsed.username || parsed.password) throw new Error("--url must be an absolute https:// URL");
  qrTerminal.generate(parsed.toString(), { small: true }, (qr) => process.stdout.write(qr));
  console.error(`APK download QR: ${parsed}`);
}
function resolveAgent(data, selector) {
  const enabled = data.agents.filter((agent) => agent.enabled);
  if (!selector) {
    if (enabled.length === 1) return enabled[0];
    throw new Error("choose a phone: nuttyproxy agents list, then use its device name");
  }
  const matches = enabled.filter((agent) => agent.deviceName === selector || agent.agentId === selector);
  if (matches.length !== 1) throw new Error(`no unique enabled phone named '${selector}'`);
  return matches[0];
}
function commandProxy(options) {
  if (options.help) return console.log("proxy [device-name]");
  const { file } = loadConfig(options); const data = loadData(dataFile(file));
  const agent = resolveAgent(data, options._[1]);
  console.log(`http://${agent.listenHost}:${agent.listenPort}`);
}
function commandService(options) {
  if (options.help || !options._[1]) return console.log("service install");
  if (options._[1] !== "install") throw new Error("unknown service command");
  installService(options);
}
function commandAgents(options) {
  if (options.help) return console.log("agents list | agents disable --agent-id p1 | agents enable --agent-id p1 | agents revoke --agent-id p1");
  const { file } = loadConfig(options); const action = options._[1] || "list"; const data = loadData(dataFile(file));
  if (action === "list") return console.table(data.agents.map(({ agentId, deviceName, serverName, listenHost, listenPort, enabled }) => ({ device: deviceName || agentId, agentId, server: serverName, proxy: `${listenHost}:${listenPort}`, enabled })));
  const agent = data.agents.find((entry) => entry.agentId === options["agent-id"]); if (!agent) throw new Error("agent not found");
  if (action === "revoke") data.agents = data.agents.filter((entry) => entry !== agent); else if (action === "disable") agent.enabled = false; else if (action === "enable") agent.enabled = true; else throw new Error("unknown agents action");
  saveData(dataFile(file), data); console.log(`${action}d ${agent.agentId}`);
}

try {
  const options = args(process.argv.slice(2)); const command = options._[0];
  if (!command || options.help && !["init", "setup", "serve", "pair", "installqr", "service", "proxy", "agents"].includes(command)) usage();
  else if (command === "init") await commandInit(options);
  else if (command === "setup") await commandSetup(options);
  else if (command === "serve") { if (options.help) console.log("serve [--config FILE]"); else { const { file, config } = loadConfig(options); runGateway(file, config); } }
  else if (command === "pair") await commandPair(options);
  else if (command === "installqr") commandInstallQr(options);
  else if (command === "service") commandService(options);
  else if (command === "proxy") commandProxy(options);
  else if (command === "agents") commandAgents(options);
  else usage();
} catch (error) { fail(error instanceof Error ? error.message : String(error)); }
