#!/usr/bin/env bash
# §7 — Đặt hàng NHIỀU DÒNG (multi-line saga) -> COMPLETED, total = 5*3000 + 2*4000 = 23000
# Chạy: bash scripts/dev/curl-07-multiline.sh
# Phụ thuộc: finance ĐỘC CHIẾM :9094 (có RecordExpense); DTO Place.lines đã fix (Long unitPriceMinor).
set -uo pipefail
GW="${GW:-http://localhost:8080}"
TENANT="${TENANT:-farm-demo}"; EMAIL="${EMAIL:-admin@example.test}"
PASSWORD="${PASSWORD:-replace-with-a-long-random-password}"
FARM="${FARM:-farm-1}"
jget(){ printf '%s' "$1" | sed -n "s/.*\"$2\"[[:space:]]*:[[:space:]]*\"\{0,1\}\([^\",}]*\)\"\{0,1\}.*/\1/p" | head -1; }

echo "== login =="
TOK=$(curl -sS -X POST "$GW/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d "{\"tenantId\":\"$TENANT\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
[ -z "$TOK" ] && { echo "LOGIN FAILED"; exit 1; }
AUTH=(-H "Authorization: Bearer $TOK" -H 'Content-Type: application/json')

WH="wh-m7-$RANDOM"
echo "== 2 item + nạp kho cả 2 vào $WH =="
I1=$(curl -sS -X POST "$GW/api/v1/inventory/items" "${AUTH[@]}" -H "Idempotency-Key: m7-i1-$RANDOM" \
  -d '{"sku":"M7-A","name":"Cám","unit":"kg"}' | { r=$(cat); jget "$r" itemId; })
I2=$(curl -sS -X POST "$GW/api/v1/inventory/items" "${AUTH[@]}" -H "Idempotency-Key: m7-i2-$RANDOM" \
  -d '{"sku":"M7-B","name":"Ngô","unit":"kg"}' | { r=$(cat); jget "$r" itemId; })
for it in "$I1" "$I2"; do
  curl -sS -o /dev/null -X POST "$GW/api/v1/inventory/receipts" "${AUTH[@]}" -H "Idempotency-Key: m7-r-$it-$RANDOM" \
    -d "{\"itemId\":\"$it\",\"farmId\":\"$FARM\",\"warehouseId\":\"$WH\",\"quantity\":\"100\",\"unit\":\"kg\"}"
done
echo "I1=$I1  I2=$I2"

echo "== đặt hàng 2 DÒNG (mỗi dòng có warehouseId) -> saga reserve+commit từng dòng =="
R=$(curl -sS -X POST "$GW/api/v1/orders" "${AUTH[@]}" -H "Idempotency-Key: m7-ord-$RANDOM" \
  -d "{\"farmId\":\"$FARM\",\"warehouseId\":\"$WH\",\"lines\":[
        {\"itemId\":\"$I1\",\"quantity\":\"5\",\"unit\":\"kg\",\"currency\":\"VND\",\"unitPriceMinor\":3000,\"warehouseId\":\"$WH\"},
        {\"itemId\":\"$I2\",\"quantity\":\"2\",\"unit\":\"kg\",\"currency\":\"VND\",\"unitPriceMinor\":4000,\"warehouseId\":\"$WH\"}]}")
echo "$R"
STATUS=$(jget "$R" status); TOTAL=$(jget "$R" totalMinor)
echo "status=$STATUS total=$TOTAL"
[ "$STATUS" = "ORDER_STATUS_COMPLETED" ] && echo "PASS §7: multi-line COMPLETED" || echo "FAIL §7: $STATUS (kiểm finance:9094 độc chiếm chưa)"
[ "$TOTAL" = "23000" ] && echo "PASS §7: total = 23000 (5*3000 + 2*4000)" || echo "FAIL §7: total=$TOTAL (muốn 23000)"
