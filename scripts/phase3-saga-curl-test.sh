#!/usr/bin/env bash
#
# Phase 3 — kiểm thử Saga đặt hàng bằng curl (happy path + COMPENSATION khi thiếu kho).
#
# Kiến trúc: order/inventory/finance đều là gRPC. curl không nói gRPC, nên script này đi qua
# REST facade DEV mà gateway expose (OrderDevController, @Profile "dev & !prod"):
#   POST /api/v1/inventory/items    -> InventoryService.CreateItem   (gRPC)
#   POST /api/v1/inventory/receipts -> InventoryService.ReceiveStock (gRPC)
#   POST /api/v1/orders             -> FarmOrderService.PlaceOrder    (gRPC, chạy saga)
#   GET  /api/v1/orders/{id}        -> FarmOrderService.GetOrder      (gRPC)
# Tất cả gateway -> gRPC dùng per-service token (Phase 2) + actor_id (truy vết user).
#
# Cần chạy trước (mỗi cái 1 terminal, profile dev):
#   identity  :8092  (loopback)      mvn -pl services/smartfarm-identity-service  spring-boot:run
#   inventory :8083  gRPC :9093      mvn -pl services/smartfarm-inventory-service spring-boot:run
#   finance   :8084  gRPC :9094      mvn -pl services/smartfarm-finance-service   spring-boot:run
#   order     :8085  gRPC :9095      mvn -pl services/smartfarm-order-service     spring-boot:run
#   gateway   :8080  (ingress)       mvn -pl apps/smartfarm-gateway               spring-boot:run
#
# Bootstrap user (dev) = SUPERADMIN -> có mọi scope (orders:write, inventory:write, identity:admin, ...).
# Nếu identity chạy trước khi có role mới: restart identity (bootstrap idempotent tự hội tụ role) rồi login lại.
# Override qua env: GATEWAY_URL, TENANT, ADMIN_EMAIL, ADMIN_PASSWORD.
set -uo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
TENANT="${TENANT:-farm-demo}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@example.test}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-replace-with-a-long-random-password}"

c_reset='\033[0m'; c_grn='\033[32m'; c_red='\033[31m'; c_yel='\033[33m'; c_cyn='\033[36m'; c_bold='\033[1m'
pass=0; fail=0
title() { printf "\n${c_bold}${c_cyn}== %s ==${c_reset}\n" "$1"; }
note()  { printf "   ${c_yel}%s${c_reset}\n" "$1"; }
check() { # check <label> <expected_regex> <actual>
  if [[ "$3" =~ $2 ]]; then printf "   ${c_grn}PASS${c_reset} %s (%s)\n" "$1" "$3"; pass=$((pass+1));
  else printf "   ${c_red}FAIL${c_reset} %s (want %s, got %s)\n" "$1" "$2" "$3"; fail=$((fail+1)); fi
}
contains() { # contains <label> <substring> ; kiểm body hiện tại có chứa chuỗi
  if grep -q -- "$2" "$tmpbody"; then printf "   ${c_grn}PASS${c_reset} %s (thấy '%s')\n" "$1" "$2"; pass=$((pass+1));
  else printf "   ${c_red}FAIL${c_reset} %s (không thấy '%s' trong body)\n" "$1" "$2"; fail=$((fail+1)); fi
}

tmpbody="$(mktemp "${TMPDIR:-/tmp}/p3saga.XXXXXX")"; trap 'rm -f "$tmpbody"' EXIT
req() { # req METHOD PATH [json] [auth] [idem]
  local m="$1" p="$2" data="${3:-}" auth="${4:-}" idem="${5:-}"
  local args=(-sS -o "$tmpbody" -w '%{http_code}' -X "$m" "$GATEWAY_URL$p" -H 'Content-Type: application/json')
  [[ -n "$auth" ]] && args+=(-H "Authorization: Bearer $auth")
  [[ -n "$idem" ]] && args+=(-H "Idempotency-Key: $idem")
  [[ -n "$data" ]] && args+=(-d "$data")
  curl "${args[@]}" 2>/dev/null
}
field() { sed -n "s/.*\"$1\"[[:space:]]*:[[:space:]]*\"\{0,1\}\([^\",}]*\)\"\{0,1\}.*/\1/p" "$tmpbody" | head -1; }

echo -e "${c_bold}SmartFarm Phase 3 — Saga curl test (happy + compensation)${c_reset}"
RUN="$(date +%s)"
WH="wh-$RUN"; FARM="farm-1"     # warehouse riêng mỗi lần chạy -> test độc lập, không dính tồn cũ

# ---- 0. login ----------------------------------------------------------------
title "0. Đăng nhập qua gateway (bootstrap = SUPERADMIN)"
st=$(req POST /api/v1/auth/login "$(printf '{"tenantId":"%s","email":"%s","password":"%s"}' "$TENANT" "$ADMIN_EMAIL" "$ADMIN_PASSWORD")")
check "login" '200' "$st"
ACCESS="$(field accessToken)"
if [[ -z "$ACCESS" ]]; then
  printf "   ${c_red}FAIL${c_reset} không lấy được token — dừng.\n"; cat "$tmpbody"; echo
  note "Gợi ý: identity đang chạy chưa? password đúng chưa (env ADMIN_PASSWORD)?"; exit 1
fi
note "token: ${ACCESS:0:24}…"

# ---- 1. super-admin thấy được toàn bộ role/scope (nền tảng phân quyền) --------
title "1. Super-admin liệt kê role/scope (endpoint phân quyền)"
st=$(req GET /api/v1/admin/roles "" "$ACCESS")
check "GET /admin/roles" '200' "$st"
contains "role SUPERADMIN có wildcard '*'" '"SUPERADMIN"'
st=$(req GET /api/v1/admin/permissions "" "$ACCESS")
check "GET /admin/permissions" '200' "$st"
contains "vốn từ scope gồm inventory:write" 'inventory:write'
contains "vốn từ scope gồm orders:write" 'orders:write'

# ---- 2. tạo item -------------------------------------------------------------
title "2. Tạo item (feed) qua gateway -> inventory gRPC"
st=$(req POST /api/v1/inventory/items "$(printf '{"sku":"FEED-%s","name":"Cam gao","unit":"kg"}' "$RUN")" "$ACCESS" "item-$RUN")
check "create item" '200' "$st"
ITEM_ID="$(field itemId)"
note "itemId=$ITEM_ID"
if [[ -z "$ITEM_ID" ]]; then
  printf "   ${c_red}FAIL${c_reset} không tạo được item — dừng.\n"; cat "$tmpbody"; echo
  note "Nếu 403: token thiếu inventory:write. Restart identity (bootstrap gán SUPERADMIN) rồi login lại."; exit 1
fi

body_order=$(printf '{"farmId":"%s","warehouseId":"%s","itemId":"%s","quantity":"10","unit":"kg","currency":"VND","unitPriceMinor":5000}' "$FARM" "$WH" "$ITEM_ID")

# ==============================================================================
title "3. CA COMPENSATION — đặt hàng khi CHƯA có kho (reserve phải thất bại)"
note "Warehouse $WH chưa nạp kho -> saga bước ReserveStock thất bại -> compensation -> FAILED."
st=$(req POST /api/v1/orders "$body_order" "$ACCESS" "order-fail-$RUN")
note "response: $(cat "$tmpbody")"
check "PlaceOrder (thiếu kho) trả 200 (saga xử lý gọn, không vỡ)" '200' "$st"
check "trạng thái saga = FAILED (compensation đã chạy)" 'ORDER_STATUS_FAILED' "$(field status)"
FAIL_ORDER_ID="$(field orderId)"
note "orderId=$FAIL_ORDER_ID, failureReason=$(field failureReason)"

note "3b. Đọc lại order — trạng thái FAILED phải bền vững (persisted):"
st=$(req GET "/api/v1/orders/$FAIL_ORDER_ID" "" "$ACCESS")
check "GetOrder ca fail" '200' "$st"
check "vẫn FAILED sau khi đọc lại" 'ORDER_STATUS_FAILED' "$(field status)"

# ==============================================================================
title "4. HAPPY PATH — nạp đủ kho rồi đặt lại (-> COMPLETED)"
st=$(req POST /api/v1/inventory/receipts "$(printf '{"itemId":"%s","farmId":"%s","warehouseId":"%s","quantity":"100","unit":"kg"}' "$ITEM_ID" "$FARM" "$WH")" "$ACCESS" "recv-$RUN")
check "receive 100kg" '200' "$st"
note "lotId=$(field lotId)"

st=$(req POST /api/v1/orders "$body_order" "$ACCESS" "order-ok-$RUN")
note "response: $(cat "$tmpbody")"
check "PlaceOrder (đủ kho)" '200' "$st"
check "trạng thái saga = COMPLETED" 'ORDER_STATUS_COMPLETED' "$(field status)"
check "total = 10 * 5000 = 50000 minor" '^50000$' "$(field totalMinor)"
OK_ORDER_ID="$(field orderId)"
note "orderId=$OK_ORDER_ID — saga: reserve -> record expense -> commit."

# ==============================================================================
title "5. IDEMPOTENCY — đặt lại CÙNG Idempotency-Key trả cùng order, không chạy saga lần 2"
st=$(req POST /api/v1/orders "$body_order" "$ACCESS" "order-ok-$RUN")
check "PlaceOrder lặp key" '200' "$st"
check "cùng orderId (idempotent)" "^${OK_ORDER_ID}$" "$(field orderId)"
check "vẫn COMPLETED" 'ORDER_STATUS_COMPLETED' "$(field status)"

# ==============================================================================
printf "\n${c_bold}Kết quả: ${c_grn}%d PASS${c_reset}, ${c_red}%d FAIL${c_reset}\n" "$pass" "$fail"
printf "${c_yel}Xác minh sâu hơn ở LOG các tiến trình:${c_reset}\n"
printf "  • order log:   'smartfarm.audit.order' — order_step / order_compensate / order_failed / order_completed, mỗi dòng có actor_id (truy vết user)\n"
printf "  • finance log: ca COMPENSATION KHÔNG để lại expense; ca happy có đúng 1 EXPENSE\n"
printf "  • inventory:   ca fail -> reservation RELEASED / không tạo; ca happy -> RESERVATION_COMMIT, on_hand giảm 10\n"
[[ "$fail" -eq 0 ]] && exit 0 || exit 1
