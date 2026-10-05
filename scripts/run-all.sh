#!/usr/bin/env bash
# run-all.sh — SmartFarm unified test runner.
#
# Order (per the suite design):
#   1. dependency check   2. load config      3. health
#   4. acquire token      5. smoke            6. REST
#   7. GraphQL schema + introspection          8. GraphQL queries
#   9. safe mutations (gated)                 10. aggregate result
#
# Exit code 0 iff every executed test passed; non-zero if any failed. Steps that
# cannot run (service down, no creds) are SKIPPED, not failed.
#
# Usage:
#   scripts/run-all.sh                 # full suite, config from scripts/config/test.env
#   scripts/run-all.sh path/to.env     # explicit env file
#   ONLY=health scripts/run-all.sh     # one phase: health|smoke|rest|graphql
set -o errexit -o nounset -o pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"
ENV_ARG="${1:-}"

START_TS=$(date +%s)

log_section "0. Dependency check"
require_cmd bash curl jq
log_success "dependencies present (bash, curl, jq)"

load_env "$ENV_ARG"

# Phase runner: run a sub-suite, accumulate pass/fail/skip from its exit code.
RUN=0; FAILED_PHASES=(); SKIPPED_PHASES=()
run_phase() {
  local label="$1" script="$2"; shift 2
  if [ -n "${ONLY:-}" ] && [ "$ONLY" != "$label" ]; then return 0; fi
  [ -f "$script" ] || { log_warn "phase '$label' script missing: $script"; return 0; }
  RUN=$((RUN+1))
  log_section ">>> PHASE: $label"
  if bash "$script" "$ENV_ARG" "$@"; then
    log_success "phase '$label' passed"
  else
    local rc=$?
    log_error "phase '$label' reported failures (exit $rc)"
    FAILED_PHASES+=("$label")
  fi
}

run_phase health   "$SCRIPT_DIR/health/test-health.sh"
run_phase smoke    "$SCRIPT_DIR/smoke/smoke-test.sh"
run_phase rest     "$SCRIPT_DIR/rest/test-all-rest.sh"
run_phase graphql  "$SCRIPT_DIR/graphql/test-all-graphql.sh"

ELAPSED=$(( $(date +%s) - START_TS ))

log_section "RESULT"
printf '  phases run:    %d\n' "$RUN" >&2
printf '  phases failed: %d%s\n' "${#FAILED_PHASES[@]}" \
  "$( [ "${#FAILED_PHASES[@]}" -gt 0 ] && printf ' (%s)' "${FAILED_PHASES[*]}" || true )" >&2
printf '  elapsed:       %ds\n' "$ELAPSED" >&2
printf '  config:        API_GATEWAY_URL=%s  GRAPHQL_URL=%s\n' "$API_GATEWAY_URL" "$GRAPHQL_URL" >&2
printf '  flags:         ALLOW_DESTRUCTIVE_TESTS=%s  GRAPHQL_INTROSPECTION_ENABLED=%s\n' \
  "$ALLOW_DESTRUCTIVE_TESTS" "$GRAPHQL_INTROSPECTION_ENABLED" >&2

if [ "${#FAILED_PHASES[@]}" -gt 0 ]; then
  log_error "SUITE FAILED"
  exit "$EXIT_FAILURES"
fi
log_success "SUITE PASSED"
exit "$EXIT_OK"
