#!/usr/bin/env bash
# assertions.sh — assertions for the SmartFarm unified test suite.
#
# Source after common.sh + http.sh. Each assertion records a pass/fail via the
# counters in common.sh and prints a clear message. GraphQL assertions check the
# JSON envelope (data / errors), NOT just the HTTP status — the gateway returns
# HTTP 200 with an "errors" array for a failed operation.
#
# IMPORTANT: assertions ALWAYS return 0. A failing assertion is recorded in the
# tally (TESTS_FAIL) and surfaced by summary(), which returns non-zero. This lets
# a test file run every assertion under `set -e` without the first failure
# aborting the whole file — only an UNEXPECTED command error aborts (true
# stop-on-error). The suite's exit code still reflects failures, via summary().
#
# All assertions read the globals HTTP_STATUS / HTTP_BODY set by http.sh.

if [ -n "${_SMARTFARM_ASSERT_SH:-}" ]; then return 0 2>/dev/null || true; fi
_SMARTFARM_ASSERT_SH=1

require_cmd jq

# assert_status <name> <expected...>  — pass if HTTP_STATUS matches any expected.
# Accepts multiple expected codes: assert_status "rbac denied" 401 403
assert_status() {
  local name="$1"; shift
  local want
  for want in "$@"; do
    if [ "$HTTP_STATUS" = "$want" ]; then pass "$name (HTTP $HTTP_STATUS)"; return 0; fi
  done
  fail "$name (want ${*}, got HTTP $HTTP_STATUS)"; _dump_body; return 0
}

# assert_json_valid <name> — pass if HTTP_BODY parses as JSON.
assert_json_valid() {
  local name="$1"
  if printf '%s' "$HTTP_BODY" | jq -e . >/dev/null 2>&1; then pass "$name (valid JSON)";
  else fail "$name (invalid JSON)"; _dump_body; fi
  return 0
}

# assert_body_nonempty <name> — pass if HTTP_BODY is non-empty / non-whitespace.
assert_body_nonempty() {
  local name="$1"
  if [ -n "${HTTP_BODY//[[:space:]]/}" ]; then pass "$name (non-empty body)";
  else fail "$name (empty body)"; fi
  return 0
}

# assert_field_exists <name> <jq-path> — e.g. assert_field_exists "me" '.subjectId'
assert_field_exists() {
  local name="$1" path="$2"
  if printf '%s' "$HTTP_BODY" | jq -e "$path != null" >/dev/null 2>&1; then
    pass "$name (field $path present)"
  else fail "$name (field $path missing)"; _dump_body; fi
  return 0
}

# assert_field_eq <name> <jq-path> <expected> — compare field value (string eq).
assert_field_eq() {
  local name="$1" path="$2" want="$3" got
  got="$(printf '%s' "$HTTP_BODY" | jq -r "$path // empty" 2>/dev/null || true)"
  if [ "$got" = "$want" ]; then pass "$name ($path == $want)";
  else fail "$name ($path: want '$want', got '$got')"; fi
  return 0
}

# ---- GraphQL-specific --------------------------------------------------------
# assert_graphql_ok <name> — a well-formed GraphQL SUCCESS response:
#   HTTP 200, valid JSON, has .data (non-null), and NO .errors.
assert_graphql_ok() {
  local name="$1"
  if [ "$HTTP_STATUS" != "200" ]; then fail "$name (GraphQL HTTP $HTTP_STATUS, want 200)"; _dump_body; return 0; fi
  if ! printf '%s' "$HTTP_BODY" | jq -e . >/dev/null 2>&1; then fail "$name (GraphQL response not JSON)"; _dump_body; return 0; fi
  if printf '%s' "$HTTP_BODY" | jq -e '.errors != null and (.errors | length > 0)' >/dev/null 2>&1; then
    fail "$name (GraphQL errors present)"
    printf '     %s\n' "$(printf '%s' "$HTTP_BODY" | jq -c '.errors' 2>/dev/null | head -c 400)" >&2
    return 0
  fi
  if printf '%s' "$HTTP_BODY" | jq -e '.data != null' >/dev/null 2>&1; then pass "$name (data, no errors)";
  else fail "$name (no data field)"; _dump_body; fi
  return 0
}

# assert_graphql_has_errors <name> — EXPECT a GraphQL error (RBAC denial,
# validation). Passes when .errors is present. Use for negative tests.
assert_graphql_has_errors() {
  local name="$1"
  if printf '%s' "$HTTP_BODY" | jq -e '.errors != null and (.errors | length > 0)' >/dev/null 2>&1; then
    pass "$name (expected GraphQL errors present)"
  else fail "$name (expected errors, none found)"; _dump_body; fi
  return 0
}

# assert_graphql_field <name> <jq-path-under-data> — e.g. '.platformStatus.status'
assert_graphql_field() {
  local name="$1" path="$2"
  if printf '%s' "$HTTP_BODY" | jq -e ".data${path} != null" >/dev/null 2>&1; then
    pass "$name (data${path} present)"
  else fail "$name (data${path} missing)"; _dump_body; fi
  return 0
}

# _dump_body — print a short, SECRET-MASKED snippet of the last response.
_dump_body() {
  local snippet; snippet="$(printf '%s' "${HTTP_BODY:-}" | head -c 400)"
  if [ -n "${AUTH_TOKEN:-}" ]; then snippet="${snippet//$AUTH_TOKEN/********}"; fi
  [ -z "$snippet" ] || printf '     %s%s%s\n' "$C_DIM" "$snippet" "$C_RESET" >&2
}
