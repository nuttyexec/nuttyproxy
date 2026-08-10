# Nutty Proxy server CLI

Nutty Proxy is one server-wide daemon. Install and configure it once on the
server; every paired phone receives its own loopback-only HTTP proxy address.
No command depends on the current directory.

## Quick start

The server needs Node.js 20+ and a public `wss://` endpoint that reverse-proxies
to this machine. Run these commands on the server.

### 1. Install the CLI

```bash
sudo env "PATH=$PATH" npm install --global \
  https://github.com/nuttyexec/nuttyproxy/releases/latest/download/nuttyproxy-cli.tgz
nuttyproxy --help
```

The URL is GitHub's `latest` release redirect, so this installs the current
published CLI without npm publishing.

### 2. Route your public WSS endpoint

Configure your normal TLS reverse proxy to send a public path to the local
gateway. Replace the hostname and path with yours.

```nginx
location = /nutty-proxy/v1 {
  proxy_pass http://127.0.0.1:41082;
  proxy_http_version 1.1;
  proxy_set_header Upgrade $http_upgrade;
  proxy_set_header Connection "upgrade";
  proxy_set_header Host $host;
  proxy_read_timeout 3600s;
  proxy_send_timeout 3600s;
  proxy_buffering off;
}
```

Obtain the TLS SPKI pin for the certificate the phone will see:

```bash
openssl s_client -connect proxy.example.com:443 -servername proxy.example.com </dev/null 2>/dev/null \
  | openssl x509 -pubkey -noout \
  | openssl pkey -pubin -outform der \
  | openssl dgst -sha256 -binary | base64
```

Prefix that value with `sha256/` when prompted below.

### 3. Configure and start the one daemon

`init` asks only for the two server-specific values. All other settings use
safe defaults: loopback gateway `127.0.0.1:41082`, global state directory
`/var/lib/nuttyproxy`, and the path already included in your WSS URL.

```bash
sudo install -d -o "$USER" -g "$(id -gn)" -m 700 /var/lib/nuttyproxy
nuttyproxy init
sudo env "PATH=$PATH" nuttyproxy service install
systemctl status nuttyproxy
```

Enter, for example, `wss://proxy.example.com/nutty-proxy/v1` and the
`sha256/...` pin at the prompts. `service install` creates and enables the
single `nuttyproxy.service`, including restart after reboot.

### 4. Show the Android download QR

```bash
nuttyproxy installqr
```

Scan this QR with the phone's regular camera, install Nutty Proxy, and open it.
The QR always points at the latest published APK.

### 5. Pair a phone

```bash
nuttyproxy pair
```

This prints a one-time pairing QR. Scan it in Nutty Proxy, choose the phone's
name in the app, approve the always-on checklist, then tap **Start proxy**.
The CLI automatically creates the opaque agent ID and selects an unused random
loopback port; it prints the resulting proxy URL beneath the QR.

### 6. Make calls through the phone

After the app says connected, list the assigned proxy address:

```bash
nuttyproxy agents list
```

Obtain the selected phone's local proxy URL once. `api.ipify.org` below is only
an example endpoint for checking the phone's egress IP:

```bash
export NUTTY_PROXY_URL="$(nuttyproxy proxy P1)"
curl --proxy "$NUTTY_PROXY_URL" https://api.ipify.org
```

HTTP methods, headers, and bodies remain normal client options. This example
sends a JSON POST through the phone; `httpbin.org/anything` is only a test URL:

```bash
curl --proxy "$NUTTY_PROXY_URL" -X POST https://httpbin.org/anything \
  --header 'Content-Type: application/json' \
  --data '{"message":"hello"}'
```

For a bot, worker, SDK, or any client that accepts proxy environment variables:

```bash
HTTP_PROXY="$NUTTY_PROXY_URL" HTTPS_PROXY="$NUTTY_PROXY_URL" python worker.py
```

Nutty Proxy never executes client commands. It manages phones and exposes the
local proxy URL; curl, SDKs, bots, and other proxy-aware tools connect to it
directly. Only local processes can reach that port. Do not expose it through a
firewall, reverse proxy, Docker port mapping, or public interface.

## Everyday commands

```bash
nuttyproxy installqr
nuttyproxy pair
nuttyproxy agents list
nuttyproxy proxy P1
```

Use the `agentId` shown by `agents list` only for access administration:

```bash
nuttyproxy agents disable --agent-id agent_xxx
nuttyproxy agents enable --agent-id agent_xxx
nuttyproxy agents revoke --agent-id agent_xxx
```

## Advanced options

The normal flow needs none of these. `pair --json` is for controlled automation,
`--qr-file /secure/path/pairing.png` writes a `0600` QR file, and explicit
agent IDs, ports, state directories, or expiry windows are for isolated tests
and integrations. The full wire contract is in [PROTOCOL.md](PROTOCOL.md).

The phone's Android Keystore private key never leaves the device. The server
stores its public key and verifies a fresh signed challenge on each connection;
the phone also pins the configured WSS certificate. The server's local callers
are trusted by design, because they can use the phone's network identity.
