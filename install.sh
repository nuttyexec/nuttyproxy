#!/bin/sh
# Installs the newest published Nutty Proxy CLI without an npm publication.
set -eu

package_url='https://github.com/nuttyexec/nuttyproxy/releases/latest/download/nuttyproxy-cli.tgz'

if ! node_bin=$(command -v node); then
  echo 'Nutty Proxy requires Node.js 20 or newer. Install Node.js first.' >&2
  exit 1
fi
if ! npm_bin=$(command -v npm); then
  echo 'Nutty Proxy requires npm. Install npm with Node.js first.' >&2
  exit 1
fi

node_major=$("$node_bin" -p 'process.versions.node.split(".")[0]')
if [ "$node_major" -lt 20 ]; then
  echo "Nutty Proxy requires Node.js 20 or newer (found $("$node_bin" --version))." >&2
  exit 1
fi

if [ "$(id -u)" -eq 0 ]; then
  "$npm_bin" install --global "$package_url"
else
  sudo env "PATH=$(dirname "$node_bin"):$PATH" "$npm_bin" install --global "$package_url"
fi

if [ "$(id -u)" -eq 0 ]; then
  npm_root=$("$npm_bin" root --global)
else
  npm_root=$(sudo env "PATH=$(dirname "$node_bin"):$PATH" "$npm_bin" root --global)
fi
entrypoint="$npm_root/@nutty-proxy/phone-proxy-agent/bin/phone-proxy-agent.mjs"
if [ ! -f "$entrypoint" ]; then
  echo 'Nutty Proxy installed, but its CLI entrypoint was not found.' >&2
  exit 1
fi

# `npm -g` may be supplied by NVM, which is intentionally absent from sudo's
# secure PATH.  A tiny root-owned wrapper keeps every documented command free
# of environment-specific PATH workarounds.
if [ "$(id -u)" -eq 0 ]; then
  printf '%s\n' '#!/bin/sh' "exec \"$node_bin\" \"$entrypoint\" \"\$@\"" \
    > /usr/local/bin/nuttyproxy
  chmod 755 /usr/local/bin/nuttyproxy
else
  printf '%s\n' '#!/bin/sh' "exec \"$node_bin\" \"$entrypoint\" \"\$@\"" \
    | sudo tee /usr/local/bin/nuttyproxy >/dev/null
  sudo chmod 755 /usr/local/bin/nuttyproxy
fi

echo 'Nutty Proxy CLI installed. Run: nuttyproxy setup'
