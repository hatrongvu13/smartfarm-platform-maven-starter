#!/usr/bin/env bash
# rest/livestock/test-rest.sh — livestock REST read surface via the gateway.
#
# Verified routes (gateway LivestockRegistryController):
#   GET /api/v1/livestock/animals?farmId=..    (SCOPE_farm:read)
#   GET /api/v1/livestock/schedules?farmId=..  (SCOPE_farm:read)
#   POST /api/v1/livestock/animals             (SCOPE_tasks:write) — destructive-gated
#
# Read-only by default. The register-animal write runs only with
# ALLOW_DESTRUCTIVE_TESTS=true and uses a unique tagCode for traceability.
set -o errexit -o nounset -o pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../../lib/common.sh"
source "$SCRIPT_DIR/../../lib/http.sh"
source "$SCRIPT_DIR/../../lib/assertions.sh"
source "$SCRIPT_DIR/../../lib/auth.sh"
load_env "${1:-}"

if ! require_login; then skip "livestock REST skipped (no usable credentials)"; summary "rest-livestock"; exit $?; fi

log_section "livestock — list animals (happy path)"
http_get "${API_GATEWAY_URL}/api/v1/livestock/animals?farmId=${TEST_FARM_ID}"
# 200 if the test account has farm:read; 403 is a valid RBAC outcome if not.
case "$HTTP_STATUS" in
  200) assert_json_valid "animals list JSON"; assert_field_exists "animals list has 'animals'" '.animals' ;;
  403) pass "animals list -> 403 (account lacks farm:read — valid RBAC result)" ;;
  *)   fail "animals list unexpected (HTTP $HTTP_STATUS)"; ;;
esac

log_section "livestock — list schedules (happy path)"
http_get "${API_GATEWAY_URL}/api/v1/livestock/schedules?farmId=${TEST_FARM_ID}"
case "$HTTP_STATUS" in
  200) assert_json_valid "schedules list JSON" ;;
  403) pass "schedules list -> 403 (valid RBAC result)" ;;
  *)   fail "schedules list unexpected (HTTP $HTTP_STATUS)" ;;
esac

log_section "livestock — validation: missing farmId is a 4xx"
http_get "${API_GATEWAY_URL}/api/v1/livestock/animals"
assert_status "animals list without farmId rejected" 400 403

log_section "livestock — write (register animal) [destructive-gated]"
if [ "${ALLOW_DESTRUCTIVE_TESTS}" = "true" ]; then
  tag="test-$(date +%s)-$$"
  body="$(jq -nc --arg f "$TEST_FARM_ID" --arg t "$tag" \
    '{farmId:$f, tagCode:$t, species:"TEST"}')"
  http_post "${API_GATEWAY_URL}/api/v1/livestock/animals" "$body"
  assert_status "register animal (tag=$tag)" 200 201 403
  [ "$HTTP_STATUS" = "200" ] && log_info "created test animal tag=$tag (no delete endpoint; data isolated by unique tag)"
else
  skip "register animal (set ALLOW_DESTRUCTIVE_TESTS=true to run; creates data)"
fi

summary "rest-livestock"
