#!/usr/bin/env bash
# 30 — Order saga business flow: compensation (thiếu kho) + happy path + list + cancel.
set -uo pipefail
source "$(dirname "$0")/_lib.sh"
FARM="farm-1"; WH="wh-$RANDOM"

echo "SmartFarm v1 — Order saga flow"
login || exit 1

hdr "1. Tạo item"
R=$(curl -sS -X POST "$GW/api/v1/inventory/items" "${AUTH[@]}" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: item-$RANDOM" -d '{"sku":"FEED-1","name":"Cám gạo","unit":"kg"}')
ITEM=$(jget "$R" itemId); [ -n "$ITEM" ] && ok "create item ($ITEM)" || { no "create item"; echo "$R"; exit 1; }

hdr "2. Ca COMPENSATION — đặt hàng khi chưa có kho -> FAILED + release"
R=$(curl -sS -X POST "$GW/api/v1/orders" "${AUTH[@]}" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: ord-fail-$RANDOM" \
  -d "{\"farmId\":\"$FARM\",\"warehouseId\":\"$WH\",\"itemId\":\"$ITEM\",\"quantity\":\"10\",\"unit\":\"kg\",\"currency\":\"VND\",\"unitPriceMinor\":5000}")
OID_FAIL=$(jget "$R" orderId)
printf '%s' "$R" | grep -q 'ORDER_STATUS_FAILED' && ok "saga FAILED khi thiếu kho (compensation chạy)" || { no "không FAILED"; echo "$R"; }

hdr "3. HAPPY PATH — nạp kho rồi đặt lại -> COMPLETED"
curl -sS -o /dev/null -X POST "$GW/api/v1/inventory/receipts" "${AUTH[@]}" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: recv-$RANDOM" \
  -d "{\"itemId\":\"$ITEM\",\"farmId\":\"$FARM\",\"warehouseId\":\"$WH\",\"quantity\":\"100\",\"unit\":\"kg\"}"
R=$(curl -sS -X POST "$GW/api/v1/orders" "${AUTH[@]}" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: ord-ok-$RANDOM" \
  -d "{\"farmId\":\"$FARM\",\"warehouseId\":\"$WH\",\"itemId\":\"$ITEM\",\"quantity\":\"10\",\"unit\":\"kg\",\"currency\":\"VND\",\"unitPriceMinor\":5000}")
OID_OK=$(jget "$R" orderId)
printf '%s' "$R" | grep -q 'ORDER_STATUS_COMPLETED' && ok "saga COMPLETED (reserve->expense->commit)" || { no "không COMPLETED"; echo "$R"; }

hdr "4. ListOrders theo farm"
R=$(curl -sS -w '\n%{http_code}' "$GW/api/v1/orders?farmId=$FARM&limit=50" "${AUTH[@]}")
CODE=$(printf '%s' "$R" | tail -1); BODY=$(printf '%s' "$R" | sed '$d')
[ "$CODE" = "200" ] && ok "GET /orders (200)" || no "list orders (got $CODE)"
printf '%s' "$BODY" | grep -q "$OID_OK" && ok "list chứa order COMPLETED" || no "list thiếu order"

hdr "5. CancelOrder — order CREATED mới (chưa saga) hoặc order FAILED"
# Hủy order đã FAILED: idempotent-ish, chuyển sang CANCELLED.
R=$(curl -sS -w '\n%{http_code}' -X POST "$GW/api/v1/orders/$OID_FAIL/cancel" "${AUTH[@]}" \
  -H 'Content-Type: application/json' -d '{"reason":"huỷ theo yêu cầu"}')
CODE=$(printf '%s' "$R" | tail -1); BODY=$(printf '%s' "$R" | sed '$d')
[ "$CODE" = "200" ] && ok "cancel order FAILED -> 200" || no "cancel (got $CODE)"
printf '%s' "$BODY" | grep -q 'ORDER_STATUS_CANCELLED' && ok "status CANCELLED" || say "status: $BODY"

hdr "6. Không cho cancel order đã COMPLETED (-> 409)"
CODE=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$GW/api/v1/orders/$OID_OK/cancel" "${AUTH[@]}" \
  -H 'Content-Type: application/json' -d '{"reason":"x"}')
[ "$CODE" = "409" ] && ok "cancel COMPLETED -> 409" || no "cancel completed (got $CODE, muốn 409)"

hdr "7. Đặt hàng NHIỀU DÒNG (multi-line saga)"
# Tạo item thứ 2 + nạp kho cả 2 trong cùng warehouse mới
WH2="wh-$RANDOM"
R=$(curl -sS -X POST "$GW/api/v1/inventory/items" "${AUTH[@]}" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: item2-$RANDOM" -d '{"sku":"FEED-2","name":"Ngô","unit":"kg"}')
ITEM2=$(jget "$R" itemId)
for it in "$ITEM" "$ITEM2"; do
  curl -sS -o /dev/null -X POST "$GW/api/v1/inventory/receipts" "${AUTH[@]}" -H 'Content-Type: application/json' \
    -H "Idempotency-Key: recv-$it-$RANDOM" \
    -d "{\"itemId\":\"$it\",\"farmId\":\"$FARM\",\"warehouseId\":\"$WH2\",\"quantity\":\"100\",\"unit\":\"kg\"}"
done
# PlaceOrder 2 dòng qua REST lines[] -> saga reserve+commit từng dòng, total = sum.
R=$(curl -sS -X POST "$GW/api/v1/orders" "${AUTH[@]}" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: ord-ml-$RANDOM" \
  -d "{\"farmId\":\"$FARM\",\"warehouseId\":\"$WH2\",\"lines\":[
        {\"itemId\":\"$ITEM\",\"quantity\":\"5\",\"unit\":\"kg\",\"currency\":\"VND\",\"unitPriceMinor\":3000},
        {\"itemId\":\"$ITEM2\",\"quantity\":\"2\",\"unit\":\"kg\",\"currency\":\"VND\",\"unitPriceMinor\":4000}]}")
printf '%s' "$R" | grep -q 'ORDER_STATUS_COMPLETED' && ok "đơn 2 dòng COMPLETED (multi-line saga)" || { no "multi-line không COMPLETED"; echo "$R"; }
# total = 5*3000 + 2*4000 = 23000
printf '%s' "$R" | grep -q '23000' && ok "total = 5*3000 + 2*4000 = 23000 (sum các dòng)" || say "total: $R"

echo
say "OrderChanged phát qua outbox mỗi lần đổi status -> MQTT (log: 'Order outbox event published')."
summary "order-saga"
