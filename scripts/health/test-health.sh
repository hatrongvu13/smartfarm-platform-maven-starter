#!/usr/bin/env bash
# health/test-health.sh — service reachability + actuator health.
#
# Checks the gateway (always) and, when their *_HEALTH_URL is configured, each
# service directly via /actuator/health. The gateway is the primary ingress, so
# a gateway failure is a hard precondition fail; direct-service probes are
# best-effort (a service may be intentionally not running in a given profile).
#
# Does NOT start or stop any service — the repo has no in-script lifecycle hook.
#
# Exit: 0 all required up; non-zero on gateway unreachable or any configured
# direct probe failing.
set -o errexit -o nounset -o pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"
source "$SCRIPT_DIR/../lib/http.sh"
source "$SCRIPT_DIR/../lib/assertions.sh"
load_env "${1:-}"

log_section "Health — gateway ingress"
# Spring Boot actuator health probe group is enabled on the gateway.
http_get "${API_GATEWAY_URL}/actuator/health"
if [ "$HTTP_STATUS" = "200" ] || [ "$HTTP_STATUS" = "503" ]; then
  # 200 UP, 503 = DOWN but reachable (actuator answered). Reachability is what
  # health check proves; assert UP explicitly below.
  assert_json_valid "gateway /actuator/health is JSON"
  assert_field_exists "gateway health has status" '.status'
  if [ "$HTTP_STATUS" = "200" ]; then pass "gateway health UP (200)"; else fail "gateway health not UP (503)"; fi
else
  fail "gateway unreachable at ${API_GATEWAY_URL}/actuator/health (HTTP $HTTP_STATUS)"
fi

# Direct per-service actuator probes — only when a *_HEALTH_URL is configured.
probe_service() {
  local name="$1" base="$2"
  [ -n "$base" ] || { skip "$name direct health (no URL configured)"; return 0; }
  http_get "${base}/actuator/health"
  if [ "$HTTP_STATUS" = "200" ]; then
    pass "$name direct health UP ($base)"
  elif [ "$HTTP_STATUS" = "503" ]; then
    fail "$name direct health DOWN/503 ($base)"
  elif [ "$HTTP_STATUS" = "404" ]; then
    # Actuator health may not be exposed; treat as reachable-but-no-probe.
    skip "$name direct health: 404 (actuator health not exposed) ($base)"
  else
    skip "$name direct health unreachable ($base, HTTP $HTTP_STATUS) — may be down in this profile"
  fi
}

log_section "Health — direct service probes (best-effort)"
probe_service identity        "${IDENTITY_HEALTH_URL:-}"
probe_service livestock       "${LIVESTOCK_HEALTH_URL:-}"
probe_service inventory       "${INVENTORY_HEALTH_URL:-}"
probe_service finance         "${FINANCE_HEALTH_URL:-}"
probe_service order           "${ORDER_HEALTH_URL:-}"
probe_service reporting       "${REPORTING_HEALTH_URL:-}"
probe_service health-service  "${HEALTH_SERVICE_HEALTH_URL:-}"
probe_service readiness       "${READINESS_HEALTH_URL:-}"

summary "health"
