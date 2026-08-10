# Nutty Proxy — Android phone agent

Nutty Proxy turns an Android phone into a persistent, outbound proxy agent. It
uses a certificate-pinned WSS tunnel to a project's own Phone Proxy Agent
gateway. The phone makes the outbound connection; no port is opened on the
phone and the server never receives a phone private key.

## Start here

1. Install the Android APK from the latest GitHub Release on the phone.
2. Install the matching `nuttyproxy` CLI release on the server, then configure
   its public `wss://` URL and certificate pin.
3. Run `nuttyproxy pair --agent-id p1 --name P1 --listen-port 42080` and scan
   the QR shown in the terminal with the app.
4. Once the app says connected, every trusted bot on that server can use:

   ```bash
   curl --proxy http://127.0.0.1:42080 https://ifconfig.me
   ```

The full copy-paste server setup and all commands are in
[`cli/README.md`](cli/README.md).

## What is implemented

- QR camera scan and manual paste of a one-time pairing JSON payload.
- P-256 device identity generated in Android Keystore. The private key is never
  exported; after enrollment the server verifies a signed challenge.
- One certificate-pinned WebSocket tunnel per paired server, multiplexing raw
  TCP proxy streams over binary frames.
- Foreground service, persistent notification, reconnect backoff, network-change
  recovery, and boot/package-update restart.
- Per-server pause/resume and local revocation; server list, connection state,
  live streams, session traffic, and a bounded local activity log.
- First-run readiness checklist for notification permission, battery exemption,
  background-data settings, and OEM app settings.

Activity logging deliberately keeps only time, server, method, and destination
host/port. It does not retain proxy request bodies, headers, credentials, or
URLs/paths.

## Generic server CLI

The companion [`cli/`](cli/) package is the reusable server-side gateway for
any project. It supplies `init`, `serve`, `pair`, `installqr`, and `agents` commands. Each
project runs its own instance and state directory, gets an outbound WSS tunnel
from the phone, and consumes the assigned `127.0.0.1:<port>` HTTP proxy.

Generate a short-lived pairing payload with the CLI, render it as a QR code,
then scan it in the app. A valid payload contains `gatewayUrl` (`wss://` only),
`certificatePin`, `agentId`, server name, and a one-time enrollment token. The
certificate pin makes a QR payload safe from ordinary DNS or CA interception.
See [`cli/README.md`](cli/README.md) for the server setup, reverse proxy
configuration, pairing flow, and lifecycle commands.

For production, install the versioned CLI tarball from this project's GitHub
Release on each server. The release asset is an npm package tarball; it keeps
the server-side CLI version explicit without requiring an npm publication. See
the CLI installation section for the exact flow.

## Build

Requires JDK 17, Android SDK platform 35, build-tools 35.0.0, and Gradle 8.9.

```bash
printf 'sdk.dir=/absolute/path/to/android-sdk\n' > local.properties
./gradlew :app:assembleDebug
```

The verified debug artifact is written to
`app/build/outputs/apk/debug/app-debug.apk`. A release signing configuration is
intentionally not committed: create and protect the signing key before putting
an APK behind the blog download QR.

## Release

Pushing a `v*` tag triggers the GitHub Actions release workflow. It builds a
signed APK and a pinned CLI `.tgz`, then creates a GitHub Release with both
assets. Before the first tag, add these **Repository secrets** in GitHub
Actions: `ANDROID_KEYSTORE_BASE64` and `ANDROID_SIGNING_PASSWORD`. The signing
alias is the non-secret fixed value `nuttyproxy`.

```bash
git tag v0.1.0
git push origin v0.1.0
```

The workflow uses the repository-scoped `GITHUB_TOKEN` with `contents: write`;
no personal access token or release-upload deploy key is needed.

Local-only design handoff material is deliberately excluded from the public
repository and from release artifacts.
