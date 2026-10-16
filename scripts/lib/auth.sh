#!/usr/bin/env bash
# auth.sh — authentication for the SmartFarm unified test suite.
#
# Source after common.sh + http.sh. Authenticates against the REAL identity
# contract, verified from source:
#
#   POST {AUTH_BASE_URL}/api/v1/auth/login
#     body:  {"tenantId":"...","email":"...","password":"..."}
#     200 ->  AuthenticationResponse {
#               authenticationStatus: COMPLETED | MFA_REQUIRED | MFA_ENROLLMENT_REQUIRED,
#               accessToken, tokenType, expiresIn, refreshToken,
#               challengeToken, availableMethods[], challengeExpiresIn }
#
# (see services/smartfarm-identity-service/.../oauth2/AuthService.java and
#  the gateway AuthProxyController that forwards /api/v1/auth/login.)
#
# Behavior:
#   - COMPLETED               -> exports AUTH_TOKEN (the access token). Reused for
#                                the whole run (login() is a no-op once set).
#   - MFA_REQUIRED            -> if TEST_TOTP_CODE is provided, completes
#                                /api/v1/auth/mfa/verify; otherwise returns 10
#                                (caller SKIPs authenticated tests, not a hard fail).
#   - MFA_ENROLLMENT_REQUIRED -> returns 11 (cannot auto-handle; caller SKIPs).
#
# The token is NEVER printed or written to the repo. Only a masked prefix is logged.

if [ -n "${_SMARTFARM_AUTH_SH:-}" ]; then return 0 2>/dev/null || true; fi
_SMARTFARM_AUTH_SH=1

require_cmd jq

AUTH_TOKEN="${AUTH_TOKEN:-}"
export AUTH_TOKEN

# auth_available — true (0) only if credentials are configured.
# auth_available — true (0) only if credentials are configured. tenantId is OPTIONAL now:
# the identity service resolves the tenant from the email (single active membership logs in
# straight away; several return a tenant list to choose from). Only username+password are needed.
auth_available() {
  [ -n "${TEST_USERNAME:-}" ] && [ -n "${TEST_PASSWORD:-}" ]
}

# login — obtain and cache a Bearer token. Idempotent within a run.
# Returns: 0 ok; 2 no creds; 1 login failed; 10 MFA required (no code);
#          11 MFA enrollment required.
login() {
  if [ -n "$AUTH_TOKEN" ]; then return 0; fi
  if ! auth_available; then
    log_warn "auth not configured (set TEST_USERNAME/TEST_PASSWORD) — skipping authenticated tests"
    return 2
  fi

  local payload
  # tenantId is optional; include it only when set (e.g. to pick one of several tenants).
  if [ -n "${TEST_TENANT_ID:-}" ]; then
    payload="$(jq -nc --arg t "$TEST_TENANT_ID" --arg e "$TEST_USERNAME" --arg p "$TEST_PASSWORD" \
      '{tenantId:$t, email:$e, password:$p}')"
  else
    payload="$(jq -nc --arg e "$TEST_USERNAME" --arg p "$TEST_PASSWORD" \
      '{email:$e, password:$p}')"
  fi

  # Call WITHOUT a bearer header (none exists yet). AUTH_TOKEN is empty here.
  http_request POST "${AUTH_BASE_URL:?AUTH_BASE_URL not set}/api/v1/auth/login" "$payload"

  if [ "$HTTP_STATUS" != "200" ]; then
    log_error "login failed (HTTP $HTTP_STATUS)"
    return 1
  fi

  local status
  status="$(printf '%s' "$HTTP_BODY" | jq -r '.authenticationStatus // empty')"
  case "$status" in
    COMPLETED)
      AUTH_TOKEN="$(printf '%s' "$HTTP_BODY" | jq -r '.accessToken // empty')"
      if [ -z "$AUTH_TOKEN" ]; then log_error "login COMPLETED but no accessToken in response"; return 1; fi
      export AUTH_TOKEN
      log_success "login COMPLETED (token ${AUTH_TOKEN:0:8}…, masked)"
      return 0
      ;;
    TENANT_SELECTION_REQUIRED)
      # Email belongs to several tenants. Re-login with an explicit TEST_TENANT_ID to pick one.
      if [ -n "${TEST_TENANT_ID:-}" ]; then
        log_warn "multiple tenants for this email; retrying with TEST_TENANT_ID"
        AUTH_TOKEN=""; TEST_TENANT_ID="$TEST_TENANT_ID" login; return $?
      fi
      log_warn "login needs a tenant choice (email has several tenants); set TEST_TENANT_ID — skipping"
      printf '%s' "$HTTP_BODY" | jq -r '.tenants[]? | "  tenant: \(.tenantCode) (\(.tenantId))"' >&2 2>/dev/null || true
      return 10
      ;;
    MFA_REQUIRED)
      if [ -n "${TEST_TOTP_CODE:-}" ]; then
        _mfa_verify "$(printf '%s' "$HTTP_BODY" | jq -r '.challengeToken // empty')" \
                    "$(printf '%s' "$HTTP_BODY" | jq -r '.availableMethods[0] // "TOTP"')"
        return $?
      fi
      log_warn "login requires MFA (set TEST_TOTP_CODE to complete) — skipping authenticated tests"
      return 10
      ;;
    MFA_ENROLLMENT_REQUIRED)
      log_warn "account requires MFA enrollment — cannot auto-authenticate; skipping"
      return 11
      ;;
    *)
      log_error "login returned unexpected authenticationStatus='$status'"
      return 1
      ;;
  esac
}

# _mfa_verify <challengeToken> <method> — complete an MFA_REQUIRED challenge
# using TEST_TOTP_CODE. Sets AUTH_TOKEN on success.
_mfa_verify() {
  local challenge="$1" method="$2" payload
  [ -n "$challenge" ] || { log_error "MFA: empty challengeToken"; return 1; }
  payload="$(jq -nc --arg c "$challenge" --arg m "$method" --arg code "$TEST_TOTP_CODE" \
    '{challengeToken:$c, method:$m, code:$code}')"
  http_request POST "${AUTH_BASE_URL}/api/v1/auth/mfa/verify" "$payload"
  if [ "$HTTP_STATUS" = "200" ]; then
    AUTH_TOKEN="$(printf '%s' "$HTTP_BODY" | jq -r '.accessToken // empty')"
    if [ -n "$AUTH_TOKEN" ]; then export AUTH_TOKEN; log_success "MFA verify OK (token masked)"; return 0; fi
  fi
  log_error "MFA verify failed (HTTP $HTTP_STATUS)"
  return 1
}

# require_login — login or exit SKIP-style. Convenience for authenticated suites:
# returns 0 if authenticated, non-zero (caller should skip) otherwise.
require_login() {
  login; local rc=$?
  return "$rc"
}
