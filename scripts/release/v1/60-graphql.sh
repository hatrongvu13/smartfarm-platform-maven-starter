#!/usr/bin/env bash
# 60 — GraphQL read surface: platformStatus, tasks, orders, dashboard, warehouseInventory,
# batchCost, cashFlow, lowStock. Tất cả đọc qua POST /graphql với Bearer token (scope như REST).
#
# Prereq: identity + livestock + inventory + finance + order + gateway chạy profile dev.
# Env override: GW, TENANT, EMAIL, PASSWORD, FARM (mặc định farm-1).
set -uo pipefail
source "$(dirname "$0")/_lib.sh"

FARM="${FARM:-farm-1}"

echo "SmartFarm v1 — GraphQL read surface"
login || exit 1

# gql <name> <query-json>  -> đặt biến BODY = response; ok/no theo có 'data' và không 'errors'
gql() {
  local name="$1" q="$2"
  BODY=$(curl -sS -X POST "$GW/graphql" "${AUTH[@]}" -H 'Content-Type: application/json' -d "$q" 2>/dev/null)
  if printf '%s' "$BODY" | grep -q '"errors"'; then
    no "$name (GraphQL errors)"; printf '     %s\n' "$(printf '%s' "$BODY" | head -c 300)"; return 1
  fi
  if printf '%s' "$BODY" | grep -q '"data"'; then ok "$name (data)"; return 0; fi
  no "$name (no data)"; printf '     %s\n' "$(printf '%s' "$BODY" | head -c 300)"; return 1
}

hdr "1. platformStatus"
gql "platformStatus" '{"query":"{ platformStatus { name status tenantId } }"}'
printf '%s' "$BODY" | grep -q '"status"' && ok "có field status" || no "thiếu status"

hdr "2. tasks + task list"
gql "tasks(farmId)" "{\"query\":\"query(\$f:ID!){ tasks(farmId:\$f, limit:5){ taskId title status } }\",\"variables\":{\"f\":\"$FARM\"}}"

hdr "3. orders list (kèm lines + warehouse-per-line)"
gql "orders(farmId)" "{\"query\":\"query(\$f:ID!){ orders(farmId:\$f, limit:5){ orderId status totalMinor lines{ itemId quantity warehouseId } } }\",\"variables\":{\"f\":\"$FARM\"}}"

hdr "4. dashboard (tổng hợp 1 round-trip)"
gql "dashboard(farmId)" "{\"query\":\"query(\$f:ID!,\$n:Int){ dashboard(farmId:\$f, recentLimit:\$n){ generatedAt tasks{ total assigned accepted completed overdueAccept overdueReport } orders{ total completed failed } overdueTasks{ taskId status } failedOrders{ orderId failureReason } } }\",\"variables\":{\"f\":\"$FARM\",\"n\":5}}"
printf '%s' "$BODY" | grep -q '"tasks"'  && ok "dashboard có tasks summary"  || no "thiếu tasks summary"
printf '%s' "$BODY" | grep -q '"orders"' && ok "dashboard có orders summary" || no "thiếu orders summary"

hdr "5. warehouseInventory (tồn kho tại item×warehouse)"
# Cần itemId thật — lấy từ env ITEM nếu có, nếu không chỉ smoke (có thể trả null nếu chưa có balance).
if [ -n "${ITEM:-}" ]; then
  gql "warehouseInventory" "{\"query\":\"query(\$i:ID!,\$w:ID!){ warehouseInventory(itemId:\$i, warehouseId:\$w){ itemId warehouseId onHand{value unit} reserved{value} available{value} } }\",\"variables\":{\"i\":\"$ITEM\",\"w\":\"${WAREHOUSE:-wh-A}\"}}"
else
  say "bỏ qua warehouseInventory (đặt ITEM=<itemId> [WAREHOUSE=wh-A] để test đầy đủ)"
fi

hdr "6. batchCost (chi phí theo lô)"
gql "batchCost" "{\"query\":\"query(\$f:ID!,\$b:ID!){ batchCost(farmId:\$f, batchId:\$b){ batchId totalCost{ currency minor } feedCost{ minor } animalCount } }\",\"variables\":{\"f\":\"$FARM\",\"b\":\"${BATCH:-batch-1}\"}}"

hdr "7. cashFlow (dòng tiền theo khoảng thời gian)"
gql "cashFlow" "{\"query\":\"query(\$f:ID!){ cashFlow(farmId:\$f){ farmId inflow{ currency minor } outflow{ minor } net{ minor } } }\",\"variables\":{\"f\":\"$FARM\"}}"

hdr "8. lowStock (tồn kho dưới ngưỡng)"
gql "lowStock" "{\"query\":\"query(\$f:ID!){ lowStock(farmId:\$f, limit:20){ itemId warehouseId available{ value unit } } }\",\"variables\":{\"f\":\"$FARM\"}}"

hdr "9. RBAC: query cần scope -> token rác bị 401/403"
CODE=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$GW/graphql" -H 'Authorization: Bearer rac.rac.rac' \
  -H 'Content-Type: application/json' -d "{\"query\":\"{ tasks(farmId:\\\"$FARM\\\"){ taskId } }\"}" 2>/dev/null)
[ "$CODE" = "401" ] || [ "$CODE" = "403" ] && ok "token rác -> $CODE" || no "token rác -> $CODE (muốn 401/403)"

summary "graphql"
