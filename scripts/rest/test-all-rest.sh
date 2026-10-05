#!/usr/bin/env bash
# rest/test-all-rest.sh — run every per-service REST test under rest/.
set -o errexit -o nounset -o pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

ENV_ARG="${1:-}"
RC=0
# Each sub-test is self-contained (sources libs, prints its own summary, exits
# with its own code). We aggregate exit codes.
for t in \
  "$SCRIPT_DIR/identity/test-rest.sh" \
  "$SCRIPT_DIR/livestock/test-rest.sh" \
  "$SCRIPT_DIR/inventory/test-rest.sh" \
  "$SCRIPT_DIR/order/test-rest.sh" \
  "$SCRIPT_DIR/reporting/test-rest.sh"
do
  [ -f "$t" ] || { log_warn "missing REST test: $t"; continue; }
  log_section "REST :: $(basename "$(dirname "$t")")"
  if bash "$t" "$ENV_ARG"; then :; else RC=1; fi
done

[ "$RC" -eq 0 ] && log_success "REST suite: all passed" || log_error "REST suite: failures present"
exit "$RC"
