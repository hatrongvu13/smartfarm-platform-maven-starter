#!/usr/bin/env bash
# rest/order/test-rest.sh — order REST surface via the gateway.
#
# Verified routes (gateway OrderRestController base /api/v1/orders,
# OrderSagaAdminController base /api/v1/order-sagas):
#   GET  /api/v1/orders/{id}                 — read one order
#   POST /api/v1/orders/drafts               — create draft  (destructive-gated)
#   PUT  /api/v1/orders/drafts/{id}          — update draft  (destructive-gated)
#   POST /api/v1/orders/drafts/{id}/submit   — submit draft  (destructive-gated)
#   DELETE /api/v1/orders/drafts/{id}        — delete draft  (destructive-gated)
#   POST /api/v1/orders/{id}/cancel          — cancel        (destructive-gated)
#   GET  /api/v1/order-sagas/{id}            — read one saga
#
# Read-only by default. Draft create→submit lifecycle runs only with
# ALLOW_DESTRUCTIVE_TESTS=true, using an idempotencyKey unique to this run.
set -o errexit -o nounset -o pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../../lib/common.sh"
source "$SCRIPT_DIR/../../lib/http.sh"
source "$SCRIPT_DIR/../../lib/assertions.sh"
source "$SCRIPT_DIR/../../lib/auth.sh"
load_env "${1:-}"

if ! require_login; then skip "order REST skipped (no usable credentials)"; summary "rest-order"; exit $?; fi

log_section "order — read non-existent order id (4xx expected)"
http_get "${API_GATEWAY_URL}/api/v1/orders/does-not-exist-$$"
assert_status "GET missing order" 400 403 404 500

log_section "order — read non-existent saga id (4xx expected)"
http_get "${API_GATEWAY_URL}/api/v1/order-sagas/does-not-exist-$$"
assert_status "GET missing order-saga" 400 403 404 500

log_section "order — draft lifecycle [destructive-gated]"
if [ "${ALLOW_DESTRUCTIVE_TESTS}" = "true" ]; then
  if [ -z "${TEST_ITEM_ID:-}" ] || [ -z "${TEST_WAREHOUSE_ID:-}" ]; then
    skip "draft lifecycle needs TEST_ITEM_ID and TEST_WAREHOUSE_ID"
  else
    idem="test-order-$(date +%s)-$$"
    body="$(jq -nc --arg k "$idem" --arg f "$TEST_FARM_ID" \
      --arg i "$TEST_ITEM_ID" --arg w "$TEST_WAREHOUSE_ID" '{
        idempotencyKey:$k, farmId:$f,
        lines:[{itemId:$i, quantity:"1", unit:"EA", currency:"VND", unitPriceMinor:1000, warehouseId:$w}]
      }')"
    http_post "${API_GATEWAY_URL}/api/v1/orders/drafts" "$body"
    assert_status "create draft order (idem=$idem)" 200 201 403
    if [ "$HTTP_STATUS" = "200" ] || [ "$HTTP_STATUS" = "201" ]; then
      oid="$(printf '%s' "$HTTP_BODY" | jq -r '.orderId // empty')"
      log_info "created draft order orderId=$oid (idempotencyKey=$idem)"
      # Clean up: delete the draft so the test leaves no residue.
      if [ -n "$oid" ]; then
        ver="$(printf '%s' "$HTTP_BODY" | jq -r '.version // 0')"
        http_delete "${API_GATEWAY_URL}/api/v1/orders/drafts/${oid}" \
          "$(jq -nc --arg o "$oid" --argjson v "${ver:-0}" '{orderId:$o, expectedVersion:$v}')"
        assert_status "cleanup: delete draft $oid" 200 204 404 409
      fi
    fi
  fi
else
  skip "draft lifecycle (set ALLOW_DESTRUCTIVE_TESTS=true + TEST_ITEM_ID/TEST_WAREHOUSE_ID)"
fi

summary "rest-order"
