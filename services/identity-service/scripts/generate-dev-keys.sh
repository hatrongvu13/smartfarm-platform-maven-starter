#!/usr/bin/env sh
# Forwarding wrapper to root scripts/generate-dev-keys.sh
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
exec "$SCRIPT_DIR/../../../scripts/generate-dev-keys.sh" "$@"
