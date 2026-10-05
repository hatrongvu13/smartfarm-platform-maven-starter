#!/usr/bin/env bash
# graphql/test-all-graphql.sh — run introspection, schema, and operation tests.
set -o errexit -o nounset -o pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

ENV_ARG="${1:-}"
RC=0
for t in \
  "$SCRIPT_DIR/test-introspection.sh" \
  "$SCRIPT_DIR/test-schema.sh" \
  "$SCRIPT_DIR/gateway/test-graphql.sh"
do
  [ -f "$t" ] || { log_warn "missing GraphQL test: $t"; continue; }
  log_section "GraphQL :: $(basename "$t")"
  if bash "$t" "$ENV_ARG"; then :; else RC=1; fi
done

[ "$RC" -eq 0 ] && log_success "GraphQL suite: all passed" || log_error "GraphQL suite: failures present"
exit "$RC"
