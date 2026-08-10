# Nutty Proxy server CLI

Nutty Proxy is one server-wide daemon. Install and configure it once on the
server; every paired phone receives its own loopback-only HTTP proxy address.
No command depends on the current directory.

## Quick start

The server needs Node.js 20+, Nginx, and either an existing HTTPS domain
(recommended) or a fixed public IP. Run these commands on the server.

### 1. Install the CLI

```bash
curl -fsSL https://raw.githubusercontent.com/nuttyexec/nuttyproxy/main/install.sh | sh
nuttyproxy --help
```

The URL is GitHub's `latest` release redirect, so this installs the current
published CLI without npm publishing.

### 2. Configure the endpoint and daemon

```bash
sudo nuttyproxy setup
```

`setup` detects the HTTPS domains already configured in Nginx. Press Enter to
use the displayed domain, or type a different configured domain. At the next
prompt, press Enter to use the standard WSS path, `/nutty-proxy`.

It adds only one exact Nginx location, calculates the certificate SPKI pin,
stores the server-wide configuration in `/var/lib/nuttyproxy`, and creates the
single boot-persistent `nuttyproxy.service`. The local gateway remains bound to
`127.0.0.1:41082`; it is never a public proxy port.

For a fixed public IP, type that IP at the address prompt. `setup` then uses a
separate HTTPS port (default `8443`) so it does not replace another site's TLS
default, and requests a six-day Let's Encrypt IP certificate. This needs
Certbot 5.3+ and briefly stops Nginx for the HTTP validation; Certbot saves the
renewal hooks and reuses the key so the app's SPKI pin remains stable. Domain
mode is preferable when a domain already exists. Active UFW installations are
opened automatically for that selected port; allow the same TCP port in any
cloud/provider firewall yourself.

The public endpoint accepts only the WSS upgrade. Before a phone becomes
usable, it must present the one-time pairing token and prove possession of its
Android-keystore device key with a fresh signed challenge. Nginx also rate- and
connection-limits that public upgrade path.

### 3. Show the Android download QR

```bash
nuttyproxy installqr
```

Scan this QR with the phone's regular camera, install Nutty Proxy, and open it.
The QR always points at the latest published APK.

### 4. Pair a phone

```bash
nuttyproxy pair
```

This prints a one-time pairing QR. Scan it in Nutty Proxy, choose the phone's
name in the app, approve the always-on checklist, then tap **Start proxy**.
The CLI automatically creates the opaque agent ID and selects an unused random
loopback port; it prints the resulting proxy URL beneath the QR.

### 5. Make calls through the phone

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
