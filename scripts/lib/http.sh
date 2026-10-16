#!/usr/bin/env bash
# http.sh — thin curl wrapper for the SmartFarm unified test suite.
#
# Source after common.sh. Provides HTTP verb helpers that split status from body,
# apply a timeout, attach a Bearer token when present, and MASK secrets in logs.
#
# Contract:
#   http_request <METHOD> <url> [json-body]
#     -> sets globals: HTTP_STATUS (int) and HTTP_BODY (string)
#     -> returns 0 always (inspect HTTP_STATUS yourself; use assertions.sh)
#   Convenience: http_get / http_post / http_put / http_patch / http_delete
#
# Auth: if AUTH_TOKEN is set (exported by auth.sh), an Authorization: Bearer
# header is added automatically. Extra headers via the HTTP_EXTRA_HEADERS array.

if [ -n "${_SMARTFARM_HTTP_SH:-}" ]; then return 0 2>/dev/null || true; fi
_SMARTFARM_HTTP_SH=1

require_cmd curl

# A string that will NEVER appear in logs — used to redact token/secret values.
_REDACT='********'

# _mask <text> — replace the live bearer token value with a placeholder so it is
# never written to logs or captured output. No-op when AUTH_TOKEN is empty.
_mask() {
  local s="$1"
  if [ -n "${AUTH_TOKEN:-}" ]; then
    s="${s//$AUTH_TOKEN/$_REDACT}"
  fi
  printf '%s' "$s"
}

# http_request METHOD URL [BODY]
# Globals set: HTTP_STATUS, HTTP_BODY
http_request() {
  local method="$1" url="$2" body="${3:-}"
  local -a curl_args=(
    --silent --show-error
    --max-time "${REQUEST_TIMEOUT_SECONDS:-30}"
    -X "$method"
    -w $'\n%{http_code}'
  )

  # Bearer token (never logged in clear).
  if [ -n "${AUTH_TOKEN:-}" ]; then
    curl_args+=(-H "Authorization: Bearer ${AUTH_TOKEN}")
  fi
  # JSON body for verbs that carry one.
  if [ -n "$body" ]; then
    curl_args+=(-H "Content-Type: application/json" --data "$body")
  fi
  # Caller-supplied extra headers (array name HTTP_EXTRA_HEADERS).
  if [ -n "${HTTP_EXTRA_HEADERS+x}" ]; then
    local h; for h in "${HTTP_EXTRA_HEADERS[@]}"; do curl_args+=(-H "$h"); done
  fi

  log_info "$method $(_mask "$url")"

  local raw rc
  # Do not let a non-2xx response (or curl transport error) abort the script:
  # we WANT the status so assertions can judge it. errexit is suspended locally.
  set +o errexit
  raw="$(curl "${curl_args[@]}" "$url" 2>/dev/null)"; rc=$?
  set -o errexit

  if [ "$rc" -ne 0 ]; then
    # Transport-level failure (DNS, refused, timeout). Surface as status 0.
    HTTP_STATUS=0
    HTTP_BODY="curl transport error (exit $rc) for $(_mask "$url")"
    log_warn "$HTTP_BODY"
    return 0
  fi

  # Last line is the %{http_code}; everything before it is the body.
  HTTP_STATUS="${raw##*$'\n'}"
  HTTP_BODY="${raw%$'\n'*}"
  # Guard: a body-less response makes body == status; normalize.
  if [ "$HTTP_BODY" = "$HTTP_STATUS" ]; then HTTP_BODY=""; fi
  case "$HTTP_STATUS" in (''|*[!0-9]*) HTTP_STATUS=0 ;; esac
  return 0
}

http_get()    { http_request GET    "$1" ""; }
http_post()   { http_request POST   "$1" "${2:-}"; }
http_put()    { http_request PUT    "$1" "${2:-}"; }
http_patch()  { http_request PATCH  "$1" "${2:-}"; }
http_delete() { http_request DELETE "$1" "${2:-}"; }

# graphql_request <query-json-payload>
#   payload is a full GraphQL HTTP body: {"query":"...","variables":{...}}
#   Posts to GRAPHQL_URL. Sets HTTP_STATUS/HTTP_BODY like http_request.
graphql_request() {
  http_request POST "${GRAPHQL_URL:?GRAPHQL_URL not set — run load_env}" "$1"
}
