#!/usr/bin/env bash
# ==============================================================================
# scripts/rand.sh (Compatibility wrapper for generate-secret.sh)
# ==============================================================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$SCRIPT_DIR/generate-secret.sh" "$@"
