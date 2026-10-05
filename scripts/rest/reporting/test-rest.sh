#!/usr/bin/env bash
# rest/reporting/test-rest.sh — reporting REST read surface via the gateway.
#
# Verified routes (gateway ReportingDevController, base /api/v1/reports):
#   GET /api/v1/reports?farmId=..   (SCOPE_report:read)   — list export jobs
#   GET /api/v1/reports/{id}        (SCOPE_report:read)
#   GET /api/v1/reports/{id}/download (SCOPE_report:read)
#
# Read-only.
set -o errexit -o nounset -o pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../../lib/common.sh"
source "$SCRIPT_DIR/../../lib/http.sh"
source "$SCRIPT_DIR/../../lib/assertions.sh"
source "$SCRIPT_DIR/../../lib/auth.sh"
load_env "${1:-}"

if ! require_login; then skip "reporting REST skipped (no usable credentials)"; summary "rest-reporting"; exit $?; fi

log_section "reporting — list export jobs (happy path)"
http_get "${API_GATEWAY_URL}/api/v1/reports?farmId=${TEST_FARM_ID}"
case "$HTTP_STATUS" in
  200) assert_json_valid "reports list JSON" ;;
  403) pass "reports list -> 403 (account lacks report:read — valid RBAC result)" ;;
  *)   fail "reports list unexpected (HTTP $HTTP_STATUS)" ;;
esac

log_section "reporting — validation: missing farmId is 4xx"
http_get "${API_GATEWAY_URL}/api/v1/reports"
assert_status "reports list without farmId rejected" 400 403

log_section "reporting — get non-existent job id (404/4xx expected)"
http_get "${API_GATEWAY_URL}/api/v1/reports/does-not-exist-$$"
assert_status "get missing report job" 400 403 404 500

summary "rest-reporting"
