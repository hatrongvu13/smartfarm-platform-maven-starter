#!/usr/bin/env bash
# common.sh — shared helpers for the SmartFarm unified test suite.
#
# Source this first from every test script:
#   SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
#   source "$SCRIPT_DIR/../lib/common.sh"
#
# Provides: logging (INFO/WARN/ERROR/SUCCESS), dependency checks, env loading,
# temp-file management, result aggregation, standardized exit codes.
#
# This library hard-codes NO ports, hosts, endpoints or secrets. Everything comes
# from scripts/config/test.env (copied from test.env.example). Values are verified
# against the SmartFarm source (apps/smartfarm-gateway + services/*).

# Guard against double-sourcing.
if [ -n "${_SMARTFARM_COMMON_SH:-}" ]; then return 0 2>/dev/null || true; fi
_SMARTFARM_COMMON_SH=1

# ---- Strict mode (callers also set this; belt and suspenders) ----------------
set -o errexit
set -o nounset
set -o pipefail

# ---- Standardized exit codes -------------------------------------------------
readonly EXIT_OK=0
readonly EXIT_FAILURES=1        # one or more tests failed
readonly EXIT_USAGE=2           # bad invocation / missing config
readonly EXIT_DEP_MISSING=3     # required dependency not installed
readonly EXIT_PRECONDITION=4    # service unreachable / preflight failed

# ---- Colors (disabled when not a TTY or NO_COLOR set) ------------------------
if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
  readonly C_RESET=$'\033[0m'; readonly C_RED=$'\033[31m'; readonly C_GRN=$'\033[32m'
  readonly C_YEL=$'\033[33m';  readonly C_CYN=$'\033[36m'; readonly C_BOLD=$'\033[1m'
  readonly C_DIM=$'\033[2m'
else
  readonly C_RESET='' C_RED='' C_GRN='' C_YEL='' C_CYN='' C_BOLD='' C_DIM=''
fi

# ---- Logging -----------------------------------------------------------------
# All logs go to stderr so stdout stays clean for machine-readable output.
_ts() { date +'%H:%M:%S'; }
log_info()    { printf '%s %s[INFO]%s  %s\n'    "$(_ts)" "$C_CYN"  "$C_RESET" "$*" >&2; }
log_warn()    { printf '%s %s[WARN]%s  %s\n'    "$(_ts)" "$C_YEL"  "$C_RESET" "$*" >&2; }
log_error()   { printf '%s %s[ERROR]%s %s\n'    "$(_ts)" "$C_RED"  "$C_RESET" "$*" >&2; }
log_success() { printf '%s %s[OK]%s    %s\n'    "$(_ts)" "$C_GRN"  "$C_RESET" "$*" >&2; }
log_section() { printf '\n%s== %s ==%s\n' "$C_BOLD" "$*" "$C_RESET" >&2; }

die() { log_error "$*"; exit "${2:-$EXIT_PRECONDITION}"; }

# ---- Dependency checks -------------------------------------------------------
# require_cmd <cmd> [<cmd> ...] — fail fast if any binary is missing.
require_cmd() {
  local missing=0 c
  for c in "$@"; do
    if ! command -v "$c" >/dev/null 2>&1; then
      log_error "required command not found: $c"
      missing=1
    fi
  done
  [ "$missing" -eq 0 ] || exit "$EXIT_DEP_MISSING"
}

# ---- Environment loading -----------------------------------------------------
# load_env [<path>] — source a KEY=VALUE env file if present. Defaults to
# scripts/config/test.env relative to this lib. Never fails if the file is
# absent (so defaults / exported vars still work), but warns once.
load_env() {
  local lib_dir env_file
  lib_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  env_file="${1:-$lib_dir/../config/test.env}"
  if [ -f "$env_file" ]; then
    # shellcheck disable=SC1090
    set -o allexport; source "$env_file"; set +o allexport
    log_info "loaded config: $env_file"
  else
    log_warn "config file not found: $env_file (using exported vars / defaults)"
  fi

  # Core endpoints — verified against gateway application.yml (server.port: 8080).
  # The gateway is the single ingress for REST (/api/v1/*) and GraphQL (/graphql).
  API_GATEWAY_URL="${API_GATEWAY_URL:-http://localhost:8080}"
  GRAPHQL_URL="${GRAPHQL_URL:-$API_GATEWAY_URL/graphql}"
  AUTH_BASE_URL="${AUTH_BASE_URL:-$API_GATEWAY_URL}"   # auth is proxied through the gateway
  REQUEST_TIMEOUT_SECONDS="${REQUEST_TIMEOUT_SECONDS:-30}"
  GRAPHQL_INTROSPECTION_ENABLED="${GRAPHQL_INTROSPECTION_ENABLED:-true}"
  ALLOW_DESTRUCTIVE_TESTS="${ALLOW_DESTRUCTIVE_TESTS:-false}"
  # Multi-tenant login body is {tenantId,email,password} (identity AuthService.Login).
  TEST_TENANT_ID="${TEST_TENANT_ID:-}"
  TEST_USERNAME="${TEST_USERNAME:-}"
  TEST_PASSWORD="${TEST_PASSWORD:-}"
  TEST_FARM_ID="${TEST_FARM_ID:-farm-1}"
  export API_GATEWAY_URL GRAPHQL_URL AUTH_BASE_URL REQUEST_TIMEOUT_SECONDS \
         GRAPHQL_INTROSPECTION_ENABLED ALLOW_DESTRUCTIVE_TESTS \
         TEST_TENANT_ID TEST_USERNAME TEST_PASSWORD TEST_FARM_ID
}

# ---- Temp-file management ----------------------------------------------------
# mktemp wrappers that auto-clean on exit. Honors TMPDIR.
_TMP_FILES=()
tmp_file() {
  local f; f="$(mktemp "${TMPDIR:-/tmp}/smartfarm-test.XXXXXX")"
  _TMP_FILES+=("$f"); printf '%s' "$f"
}
_cleanup_tmp() { [ "${#_TMP_FILES[@]}" -eq 0 ] || rm -f "${_TMP_FILES[@]}" 2>/dev/null || true; }
trap _cleanup_tmp EXIT

# ---- Result aggregation ------------------------------------------------------
# A test file calls pass/fail/skip; summary() prints the tally and returns
# non-zero if any failed. Counters are plain globals so a sourced sub-helper
# can increment them.
TESTS_RUN=0; TESTS_PASS=0; TESTS_FAIL=0; TESTS_SKIP=0
FAILED_NAMES=()

pass() { TESTS_RUN=$((TESTS_RUN+1)); TESTS_PASS=$((TESTS_PASS+1)); printf '   %sPASS%s %s\n' "$C_GRN" "$C_RESET" "$*" >&2; }
fail() { TESTS_RUN=$((TESTS_RUN+1)); TESTS_FAIL=$((TESTS_FAIL+1)); FAILED_NAMES+=("$*"); printf '   %sFAIL%s %s\n' "$C_RED" "$C_RESET" "$*" >&2; }
skip() { TESTS_RUN=$((TESTS_RUN+1)); TESTS_SKIP=$((TESTS_SKIP+1)); printf '   %sSKIP%s %s\n' "$C_YEL" "$C_RESET" "$*" >&2; }

# summary [<label>] — prints tally, lists failures, returns EXIT_OK/EXIT_FAILURES.
summary() {
  local label="${1:-result}"
  printf '\n%s---- %s ----%s\n' "$C_BOLD" "$label" "$C_RESET" >&2
  printf '  total=%d  %spass=%d%s  %sfail=%d%s  %sskip=%d%s\n' \
    "$TESTS_RUN" "$C_GRN" "$TESTS_PASS" "$C_RESET" \
    "$C_RED" "$TESTS_FAIL" "$C_RESET" "$C_YEL" "$TESTS_SKIP" "$C_RESET" >&2
  if [ "$TESTS_FAIL" -gt 0 ]; then
    printf '  failed:\n' >&2
    local n; for n in "${FAILED_NAMES[@]}"; do printf '    - %s\n' "$n" >&2; done
    return "$EXIT_FAILURES"
  fi
  return "$EXIT_OK"
}
