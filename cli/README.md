# `phone-proxy-agent` CLI

This is the generic server-side control plane for the Android app. It is not
tied to a single domain or any particular workload. Any project can run
one instance, expose its WSS endpoint, and consume each enrolled phone through
the loopback HTTP proxy port assigned at pairing.

## Install from a GitHub Release

Each release publishes `nutty-proxy-phone-proxy-agent-<version>.tgz`, generated
with `npm pack`. Download the exact release version on the project server; this
does not require the package to be published to npm.

```bash
release_version=0.1.0
install_dir=/opt/nutty-proxy-agent
mkdir -p "$install_dir"
curl -fL "https://github.com/<owner>/<repo>/releases/download/v${release_version}/nutty-proxy-phone-proxy-agent-${release_version}.tgz" \
  -o /tmp/nutty-proxy-agent.tgz
tar -xzf /tmp/nutty-proxy-agent.tgz --strip-components=1 -C "$install_dir"
cd "$install_dir"
npm ci --omit=dev
node bin/phone-proxy-agent.mjs --help
sudo ln -sf "$install_dir/bin/phone-proxy-agent.mjs" /usr/local/bin/nuttyproxy
```

Replace `<owner>/<repo>` with this repository's public GitHub path before
publishing the release. Verify the release checksum if one is supplied. The
release tarball intentionally contains only the CLI runtime, lockfile, and
operator/protocol documentation.

## Configure

```bash
nuttyproxy init \
  --public-url wss://proxy.example.com/phone-agent/v1 \
  --certificate-pin 'sha256/<SPKI-base64-pin>'
nuttyproxy serve
```

`serve` binds to `127.0.0.1:41082` by default. Terminate TLS at your normal
reverse proxy; the public URL must route `/phone-agent/v1` with WebSocket
upgrade support. Never expose the agent's assigned HTTP proxy port publicly:
the CLI binds it to loopback only.

```nginx
location = /phone-agent/v1 {
  proxy_pass http://127.0.0.1:41082;
  proxy_http_version 1.1;
  proxy_set_header Upgrade $http_upgrade;
  proxy_set_header Connection "upgrade";
  proxy_set_header Host $host;
  proxy_read_timeout 3600s;
}
```

Apply connection and handshake rate limits at the reverse proxy appropriate to
your expected phone count. The gateway enforces a 2 MiB WebSocket message limit,
but TLS termination is still the right place to absorb unauthenticated
connection floods.

Obtain the SPKI pin from the certificate your phones will see, for example:

```bash
openssl s_client -connect proxy.example.com:443 -servername proxy.example.com </dev/null 2>/dev/null \
  | openssl x509 -pubkey -noout \
  | openssl pkey -pubin -outform der \
  | openssl dgst -sha256 -binary | base64
```

Prefix the result with `sha256/`.

For a persistent deployment, run `nuttyproxy serve` under your normal
service supervisor with a dedicated unprivileged user and a state directory
such as `/var/lib/phone-proxy-agent` (mode `0700`), not in a source checkout.

## Pair a phone

```bash
nuttyproxy pair --agent-id p1 --name P1 --listen-port 42080
```

`pair` renders a scannable terminal QR by default. Add `--json` when automation
needs JSON only; `--qr-file /secure/path/p1.png` writes a `0600` PNG. Use it
only in a private operator workflow and remove it after pairing. The generated JSON is a short-lived,
single-use pairing secret. Do not commit it, embed it in a public blog image, or
use it as the APK download QR. Scan it in the Android app, then use your
service normally through its assigned loopback proxy:

```bash
curl --proxy http://127.0.0.1:42080 https://ifconfig.me
```

## Manage access

```bash
nuttyproxy agents list
nuttyproxy agents disable --agent-id p1
nuttyproxy agents enable --agent-id p1
nuttyproxy agents revoke --agent-id p1
```

Disabling or revoking access immediately closes the active tunnel and its
loopback proxy listener. Re-pair a revoked phone with a new one-time payload.

## Trust and protocol boundary

The Android app always creates the private key locally in Android Keystore. The
CLI persists only its public JWK and verifies a fresh signature challenge on
each connection. The app pins the public WSS certificate in the pairing
payload. HTTP `CONNECT` and regular absolute-form `http://` proxy traffic are
multiplexed inside the encrypted WSS tunnel.

Running this CLI means the hosting project trusts its own local callers to use
the assigned loopback proxy port. If several unrelated workloads share a host,
give each project a separate CLI state directory and non-overlapping proxy port
range. `127.0.0.1` is not a per-user security boundary: do not give untrusted
local users shell access to a host with an active proxy listener. Use separate
VMs/containers with separate network namespaces when that boundary is needed.

The exact on-wire contract is in [PROTOCOL.md](PROTOCOL.md). It is the right
reference when adding another server implementation or a non-Android client.

## Development checkout

`nuttyproxy installqr` prints the matching GitHub Release APK QR. Pass `--url`
to render a different HTTPS APK URL. For local development only, run `npm install`
inside `cli/` and invoke `node bin/phone-proxy-agent.mjs`. Production servers
should use a pinned GitHub Release tarball rather than a mutable checkout or
global npm install.
