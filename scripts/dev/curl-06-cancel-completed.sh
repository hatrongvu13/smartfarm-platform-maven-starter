#!/usr/bin/env bash
# §6 — Không cho cancel order đã COMPLETED (-> 409)
# Chạy: bash scripts/dev/curl-06-cancel-completed.sh
# Phụ thuộc: finance ĐỘC CHIẾM :9094 (không trùng health), có RecordExpense.
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

WH="wh-c6-$RANDOM"
echo "== tạo item + nạp kho ($WH) =="
ITEM=$(curl -sS -X POST "$GW/api/v1/inventory/items" "${AUTH[@]}" -H "Idempotency-Key: c6-item-$RANDOM" \
  -d '{"sku":"C6","name":"Feed C6","unit":"kg"}' | { r=$(cat); echo "$(jget "$r" itemId)"; })
curl -sS -o /dev/null -X POST "$GW/api/v1/inventory/receipts" "${AUTH[@]}" -H "Idempotency-Key: c6-recv-$RANDOM" \
  -d "{\"itemId\":\"$ITEM\",\"farmId\":\"$FARM\",\"warehouseId\":\"$WH\",\"quantity\":\"100\",\"unit\":\"kg\"}"
echo "itemId=$ITEM"

echo "== đặt hàng happy-path -> phải COMPLETED (finance RecordExpense chạy) =="
OResp=$(curl -sS -X POST "$GW/api/v1/orders" "${AUTH[@]}" -H "Idempotency-Key: c6-ord-$RANDOM" \
  -d "{\"farmId\":\"$FARM\",\"warehouseId\":\"$WH\",\"itemId\":\"$ITEM\",\"quantity\":\"10\",\"unit\":\"kg\",\"currency\":\"VND\",\"unitPriceMinor\":5000}")
echo "$OResp"
OID=$(jget "$OResp" orderId)
STATUS=$(jget "$OResp" status)
echo "orderId=$OID status=$STATUS"
[ "$STATUS" != "ORDER_STATUS_COMPLETED" ] && { echo "!! order KHÔNG COMPLETED (kiểm finance:9094) — §6 không kiểm được"; exit 1; }

echo "== HỦY order đã COMPLETED -> kỳ vọng HTTP 409 =="
CODE=$(curl -sS -o /tmp/c6body -w '%{http_code}' -X POST "$GW/api/v1/orders/$OID/cancel" "${AUTH[@]}" -d '{"reason":"x"}')
echo "HTTP $CODE"; cat /tmp/c6body 2>/dev/null; echo
[ "$CODE" = "409" ] && echo "PASS §6: cancel COMPLETED -> 409" || echo "FAIL §6: got $CODE (muốn 409)"
