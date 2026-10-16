#!/usr/bin/env bash
# Run npm with the project's Node 24 without changing the global installation.
set -euo pipefail

webox_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
webox_node="${WEBOX_NODE_BIN:-$webox_root/.local/toolchains/node24/bin/node}"
if [[ ! -x "$webox_node" ]]; then
  if [[ -n "${WEBOX_NODE_BIN:-}" ]]; then
    printf 'WEBOX_NODE_BIN is not executable: %s\n' "$webox_node" >&2
    exit 1
  fi
  webox_node="$(command -v node || true)"
fi
if [[ -z "$webox_node" ]]; then
  printf 'Node 24 is required. Set WEBOX_NODE_BIN to its absolute executable path.\n' >&2
  exit 1
fi
webox_node="$(cd "$(dirname "$webox_node")" && pwd)/$(basename "$webox_node")"
webox_node_version="$("$webox_node" --version)"
case "$webox_node_version" in
  v24.*) ;;
  *) printf 'Node 24 is required; found %s (%s).\n' "$webox_node_version" "$webox_node" >&2; exit 1 ;;
esac

export PATH="$(dirname "$webox_node"):$PATH"
if ! command -v npm >/dev/null 2>&1; then
  printf 'npm 11 is required and was not found on PATH.\n' >&2
  exit 1
fi
webox_npm_version="$(npm --version)"
case "$webox_npm_version" in
  11.*) ;;
  *) printf 'npm 11 is required; found %s.\n' "$webox_npm_version" >&2; exit 1 ;;
esac

export npm_config_cache="${npm_config_cache:-$webox_root/.local/npm}"
cd "$webox_root/frontend"
exec npm "$@"
