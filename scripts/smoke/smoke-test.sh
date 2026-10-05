#!/usr/bin/env bash
# smoke/smoke-test.sh — fast end-to-end smoke test through the gateway.
#
# Minimal "is the platform wired up?" check: gateway health, the GraphQL endpoint
# answers, OpenAPI docs reachable (if exposed), and — when creds are configured —
# an authenticated whoami. No data mutations. Finishes in seconds.
set -o errexit -o nounset -o pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"
source "$SCRIPT_DIR/../lib/http.sh"
source "$SCRIPT_DIR/../lib/assertions.sh"
source "$SCRIPT_DIR/../lib/auth.sh"
load_env "${1:-}"

log_section "Smoke — gateway up"
http_get "${API_GATEWAY_URL}/actuator/health"
assert_status "gateway health reachable" 200 503

log_section "Smoke — GraphQL endpoint answers"
# platformStatus requires no args; may require auth depending on security config.
# A 200 with data OR an auth error both prove the endpoint is live and wired.
graphql_request '{"query":"{ platformStatus { name status } }"}'
if [ "$HTTP_STATUS" = "200" ]; then
  if printf '%s' "$HTTP_BODY" | jq -e '.data.platformStatus != null' >/dev/null 2>&1; then
    pass "graphql platformStatus (unauthenticated allowed)"
  elif printf '%s' "$HTTP_BODY" | jq -e '.errors != null' >/dev/null 2>&1; then
    pass "graphql endpoint live (returned errors envelope — likely auth required)"
  else
    fail "graphql endpoint returned 200 but neither data nor errors"
  fi
elif [ "$HTTP_STATUS" = "401" ] || [ "$HTTP_STATUS" = "403" ]; then
  pass "graphql endpoint live (auth required: HTTP $HTTP_STATUS)"
else
  fail "graphql endpoint not answering (HTTP $HTTP_STATUS)"
fi

log_section "Smoke — OpenAPI docs (if exposed)"
http_get "${API_GATEWAY_URL}/v3/api-docs"
if [ "$HTTP_STATUS" = "200" ]; then
  assert_json_valid "openapi /v3/api-docs is JSON"
else
  skip "openapi /v3/api-docs not exposed here (HTTP $HTTP_STATUS)"
fi

log_section "Smoke — authenticated whoami (if creds configured)"
if require_login; then
  http_get "${API_GATEWAY_URL}/api/v1/me"
  assert_status "GET /api/v1/me" 200
  assert_json_valid "/api/v1/me is JSON"
else
  skip "authenticated smoke skipped (no usable credentials / MFA required)"
fi

summary "smoke"
