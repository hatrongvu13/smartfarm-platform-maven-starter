#!/usr/bin/env bash
# rest/identity/test-rest.sh — identity/auth REST surface via the gateway.
#
# Verified routes (gateway AuthProxyController + WhoAmIController + identity AuthController):
#   POST /api/v1/auth/login        (public; {tenantId,email,password})
#   GET  /api/v1/me                (authenticated; whoami)
#   GET  /.well-known/jwks.json    (public; served by identity, reachable via its base)
#
# Read-only. No user creation / mutation.
set -o errexit -o nounset -o pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../../lib/common.sh"
source "$SCRIPT_DIR/../../lib/http.sh"
source "$SCRIPT_DIR/../../lib/assertions.sh"
source "$SCRIPT_DIR/../../lib/auth.sh"
load_env "${1:-}"

log_section "identity — public endpoints"
# login validation: a bad/empty login must NOT 200 (negative/validation path).
http_post "${AUTH_BASE_URL}/api/v1/auth/login" '{"tenantId":"","email":"","password":""}'
assert_status "login with empty creds rejected" 400 401 403 422

log_section "identity — authenticated whoami"
if require_login; then
  http_get "${API_GATEWAY_URL}/api/v1/me"
  assert_status "GET /api/v1/me" 200
  assert_json_valid "/api/v1/me JSON"
  assert_body_nonempty "/api/v1/me body"
else
  skip "whoami skipped (no usable credentials / MFA required)"
fi

log_section "identity — RBAC: no token is rejected on protected route"
# Probe without a bearer token. Save/restore AUTH_TOKEN in THIS shell so the
# counters update the real summary (a subshell would swallow them).
_saved_token="${AUTH_TOKEN:-}"
AUTH_TOKEN=""
http_get "${API_GATEWAY_URL}/api/v1/me"
assert_status "GET /api/v1/me without token" 401 403
AUTH_TOKEN="$_saved_token"

summary "rest-identity"
