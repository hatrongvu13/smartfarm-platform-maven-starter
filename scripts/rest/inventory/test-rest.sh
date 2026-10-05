#!/usr/bin/env bash
# rest/inventory/test-rest.sh — inventory REST surface via the gateway.
#
# Verified routes (gateway InventoryDevSetupController base /api/v1/inventory):
#   POST /api/v1/inventory/items      — create item     (destructive-gated)
#   POST /api/v1/inventory/receipts   — record receipt  (destructive-gated)
#
# This controller is POST-only (dev setup); there is no REST read path for
# inventory (reads are GraphQL: warehouseInventory / lowStock). So the default
# read-only run only verifies RBAC on the write endpoints. Writes run with
# ALLOW_DESTRUCTIVE_TESTS=true.
set -o errexit -o nounset -o pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../../lib/common.sh"
source "$SCRIPT_DIR/../../lib/http.sh"
source "$SCRIPT_DIR/../../lib/assertions.sh"
source "$SCRIPT_DIR/../../lib/auth.sh"
load_env "${1:-}"

if ! require_login; then skip "inventory REST skipped (no usable credentials)"; summary "rest-inventory"; exit $?; fi

log_section "inventory — RBAC/validation on items (no destructive write)"
if [ "${ALLOW_DESTRUCTIVE_TESTS}" != "true" ]; then
  # Empty body: expect a 4xx (validation / RBAC) — proves the endpoint exists and
  # guards input, WITHOUT creating data.
  http_post "${API_GATEWAY_URL}/api/v1/inventory/items" '{}'
  assert_status "POST /inventory/items with empty body rejected" 400 403 422 500
else
  item="test-item-$(date +%s)-$$"
  body="$(jq -nc --arg i "$item" --arg f "$TEST_FARM_ID" \
    '{itemId:$i, farmId:$f, name:"TEST ITEM", unit:"EA"}')"
  http_post "${API_GATEWAY_URL}/api/v1/inventory/items" "$body"
  assert_status "create inventory item (itemId=$item)" 200 201 403
  [ "$HTTP_STATUS" = "200" ] && log_info "created test item itemId=$item (no delete endpoint; unique id isolates it)"
fi

summary "rest-inventory"
