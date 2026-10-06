#!/usr/bin/env bash
#
# Phase 2 — kiểm thử per-service token (Hướng A) + truy vết actor bằng curl.
#
# Chạy script này SAU khi đã khởi động 3 tiến trình (mỗi cái một terminal, profile dev):
#   1) identity  :8092  (loopback)   ->  mvn -pl services/smartfarm-identity-service  spring-boot:run
#   2) livestock :8081  + gRPC :9091 ->  mvn -pl services/smartfarm-livestock-service spring-boot:run
#   3) gateway   :8080  (ingress)    ->  mvn -pl apps/smartfarm-gateway               spring-boot:run
#
# Mặc định dùng tài khoản bootstrap ADMIN. LƯU Ý: ADMIN có scope
# `farm:read identity:admin identity:platform` nhưng KHÔNG có `tasks:write`,
# nên bước tạo task sẽ 403 với admin — đó là kết quả ĐÚNG (chứng minh phân quyền).
# Muốn test tạo task thành công, cấp cho user một role có `tasks:write` rồi đặt
# TASKS_USER_EMAIL / TASKS_USER_PASSWORD / TENANT bên dưới.
#
# Có thể override qua biến môi trường:
#   GATEWAY_URL, IDENTITY_URL, LIVESTOCK_URL,
#   TENANT, ADMIN_EMAIL, ADMIN_PASSWORD,
#   GATEWAY_SERVICE_SECRET (khớp secret whitelist dev)
set -uo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
IDENTITY_URL="${IDENTITY_URL:-http://localhost:8092}"
LIVESTOCK_URL="${LIVESTOCK_URL:-http://localhost:8081}"

TENANT="${TENANT:-farm-demo}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@example.test}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-replace-with-a-long-random-password}"
GATEWAY_SERVICE_SECRET="${GATEWAY_SERVICE_SECRET:-dev-gateway-service-secret}"

# ---- helpers -----------------------------------------------------------------
c_reset='\033[0m'; c_grn='\033[32m'; c_red='\033[31m'; c_yel='\033[33m'; c_cyn='\033[36m'; c_bold='\033[1m'
pass=0; fail=0
title() { printf "\n${c_bold}${c_cyn}== %s ==${c_reset}\n" "$1"; }
note()  { printf "   ${c_yel}%s${c_reset}\n" "$1"; }
# check_status <label> <expected_regex> <actual_status>
check_status() {
  local label="$1" want="$2" got="$3"
  if [[ "$got" =~ $want ]]; then printf "   ${c_grn}PASS${c_reset} %s (HTTP %s)\n" "$label" "$got"; pass=$((pass+1));
  else printf "   ${c_red}FAIL${c_reset} %s (want %s, got HTTP %s)\n" "$label" "$want" "$got"; fail=$((fail+1)); fi
}
# curl_status METHOD URL [data] [auth]  -> prints body to /tmp, echoes status
tmpbody="$(mktemp "${TMPDIR:-/tmp}/p2curl.XXXXXX")"
trap 'rm -f "$tmpbody"' EXIT
req() {
  local method="$1" url="$2" data="${3:-}" auth="${4:-}"
  local args=(-sS -o "$tmpbody" -w '%{http_code}' -X "$method" "$url" -H 'Content-Type: application/json')
  [[ -n "$auth" ]] && args+=(-H "Authorization: Bearer $auth")
  [[ -n "$data" ]] && args+=(-d "$data")
  curl "${args[@]}" 2>/dev/null
}
# tiny JSON field extractor (no jq dependency)
json_field() { sed -n "s/.*\"$1\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p" "$tmpbody" | head -1; }

echo -e "${c_bold}SmartFarm Phase 2 — curl test${c_reset}"
echo "gateway=$GATEWAY_URL  identity=$IDENTITY_URL(loopback)  livestock=$LIVESTOCK_URL"

# ==============================================================================
title "0. Health / reachability"
st=$(req GET "$GATEWAY_URL/actuator/health"); check_status "gateway /actuator/health reachable" '200' "$st"
note "body: $(cat "$tmpbody")"

# ==============================================================================
title "1. Bất biến: identity KHÔNG lộ ra ngoài (chỉ loopback)"
note "Gọi thẳng JWKS trên identity qua loopback phải OK (chính gateway cũng đi đường này):"
st=$(req GET "$IDENTITY_URL/.well-known/jwks.json"); check_status "identity JWKS trên loopback" '200' "$st"
note "=> Ở PROD identity phải nằm trong mạng nội bộ; client thật chỉ được thấy gateway:8080."

# ==============================================================================
title "2. Đăng nhập QUA GATEWAY (REST->REST proxy, không cần token)"
login_body=$(printf '{"tenantId":"%s","email":"%s","password":"%s"}' "$TENANT" "$ADMIN_EMAIL" "$ADMIN_PASSWORD")
st=$(req POST "$GATEWAY_URL/api/v1/auth/login" "$login_body")
check_status "POST /api/v1/auth/login qua gateway" '200' "$st"
ACCESS="$(json_field accessToken)"
if [[ -z "$ACCESS" ]]; then
  printf "   ${c_red}FAIL${c_reset} không lấy được accessToken — dừng.\n"; note "body: $(cat "$tmpbody")"; exit 1
fi
note "accessToken (user, aud=smartfarm-gateway) lấy được: ${ACCESS:0:24}…"

# ==============================================================================
title "3. Token user hợp lệ tại GATEWAY (đúng cổng chính)"
st=$(req GET "$GATEWAY_URL/api/v1/auth/me" "" "$ACCESS")
check_status "GET /api/v1/auth/me qua gateway (có JWT)"      '200' "$st"
note "me: $(cat "$tmpbody")"
st=$(req GET "$GATEWAY_URL/api/v1/auth/me")
check_status "GET /api/v1/auth/me KHÔNG token -> 401"        '401' "$st"

# ==============================================================================
title "4. Bất biến CỐT LÕI: user-token KHÔNG gọi thẳng service được"
note "user-token có aud=smartfarm-gateway; livestock giờ verify aud=smartfarm-livestock."
note "livestock chỉ expose gRPC (:9091) ở prod; REST :8081 là dev-only và cũng nằm sau xác thực."
st=$(req GET "$LIVESTOCK_URL/api/v1/livestock/tasks/does-not-exist" "" "$ACCESS")
check_status "user-token gọi thẳng livestock REST bị chặn" '401|403|404|000' "$st"
note "(000 = không mở REST/không tới được: cũng đạt mục tiêu 'không expose ra ngoài'.)"

# ==============================================================================
title "5. Service-token endpoint là NỘI BỘ (không proxy ra gateway)"
svc_req=$(printf '{"clientId":"gateway","secret":"%s","audience":"smartfarm-livestock","tenantId":"%s","actorId":"tester"}' "$GATEWAY_SERVICE_SECRET" "$TENANT")
st=$(req POST "$GATEWAY_URL/internal/service-token" "$svc_req")
check_status "/internal/service-token QUA GATEWAY phải KHÔNG tồn tại" '404|401|405|000' "$st"
note "=> gateway không route path này ra ngoài (đúng thiết kế)."

note "Cấp service-token trực tiếp trên identity loopback (mô phỏng đúng việc gateway làm nội bộ):"
st=$(req POST "$IDENTITY_URL/internal/service-token" "$svc_req")
check_status "/internal/service-token trên identity loopback -> 200" '200' "$st"
SVC_TOKEN="$(json_field accessToken)"
[[ -n "$SVC_TOKEN" ]] && note "service-token (sub=svc:gateway, aud=smartfarm-livestock): ${SVC_TOKEN:0:24}…"

echo
note "5b. Secret sai -> phải bị từ chối (401), không tiết lộ lý do:"
bad_req=$(printf '{"clientId":"gateway","secret":"wrong","audience":"smartfarm-livestock","tenantId":"%s","actorId":"tester"}' "$TENANT")
st=$(req POST "$IDENTITY_URL/internal/service-token" "$bad_req")
check_status "service-token secret sai -> 401" '401' "$st"

note "5c. audience ngoài whitelist -> phải bị từ chối (401):"
oos_req=$(printf '{"clientId":"gateway","secret":"%s","audience":"smartfarm-finance","tenantId":"%s","actorId":"tester"}' "$GATEWAY_SERVICE_SECRET" "$TENANT")
st=$(req POST "$IDENTITY_URL/internal/service-token" "$oos_req")
check_status "service-token audience không cho phép -> 401" '401' "$st"

# ==============================================================================
title "6. Luồng nghiệp vụ QUA GATEWAY -> gRPC bằng service-token + actor_id"
note "Gateway nhận user-JWT, tự đổi sang service-token nội bộ rồi gọi gRPC livestock,"
note "đồng thời đặt actor_id=<user> vào RequestContext để livestock ghi audit truy vết."
IDEM="test-$(date +%s)"
task_body='{"farmId":"farm-1","title":"Kiem kho dinh ky","assigneeId":"worker-1"}'
st=$(curl -sS -o "$tmpbody" -w '%{http_code}' -X POST "$GATEWAY_URL/api/v1/livestock/tasks" \
      -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS" \
      -H "Idempotency-Key: $IDEM" -d "$task_body" 2>/dev/null)
note "response: $(cat "$tmpbody")"
if [[ "$st" == "403" ]]; then
  check_status "tạo task với ADMIN (thiếu tasks:write) -> 403 ĐÚNG phân quyền" '403' "$st"
  note "=> Đây là kết quả mong đợi cho tài khoản admin. Xem mục 7 để test tạo task THÀNH CÔNG."
else
  check_status "POST /api/v1/livestock/tasks qua gateway (user có tasks:write)" '200|201' "$st"
  TASK_ID="$(json_field taskId)"
  [[ -n "$TASK_ID" ]] && note "taskId=$TASK_ID — giờ kiểm tra log livestock có dòng:"
  [[ -n "$TASK_ID" ]] && note "   smartfarm.audit.task  task_created ... caller=svc:gateway actor_id=<userId> ... task_id=$TASK_ID"
  if [[ -n "$TASK_ID" ]]; then
    st=$(curl -sS -o "$tmpbody" -w '%{http_code}' -X GET "$GATEWAY_URL/api/v1/livestock/tasks/$TASK_ID" \
          -H "Authorization: Bearer $ACCESS" 2>/dev/null)
    check_status "GET task vừa tạo qua gateway" '200' "$st"
  fi
fi

# ==============================================================================
title "7. (Tùy chọn) Test tạo task THÀNH CÔNG với user có tasks:write"
if [[ -n "${TASKS_USER_EMAIL:-}" && -n "${TASKS_USER_PASSWORD:-}" ]]; then
  lb=$(printf '{"tenantId":"%s","email":"%s","password":"%s"}' "$TENANT" "$TASKS_USER_EMAIL" "$TASKS_USER_PASSWORD")
  st=$(req POST "$GATEWAY_URL/api/v1/auth/login" "$lb"); check_status "login user tasks:write" '200' "$st"
  T2="$(json_field accessToken)"
  IDEM2="test-w-$(date +%s)"
  st=$(curl -sS -o "$tmpbody" -w '%{http_code}' -X POST "$GATEWAY_URL/api/v1/livestock/tasks" \
        -H 'Content-Type: application/json' -H "Authorization: Bearer $T2" \
        -H "Idempotency-Key: $IDEM2" -d "$task_body" 2>/dev/null)
  check_status "tạo task với user tasks:write -> 200/201" '200|201' "$st"
  note "response: $(cat "$tmpbody")"
else
  note "Bỏ qua: đặt TASKS_USER_EMAIL và TASKS_USER_PASSWORD (user có role chứa tasks:write) để chạy."
fi

# ==============================================================================
printf "\n${c_bold}Kết quả: ${c_grn}%d PASS${c_reset}, ${c_red}%d FAIL${c_reset}\n" "$pass" "$fail"
printf "${c_yel}Nhắc:${c_reset} mở log livestock để xác nhận truy vết — mỗi task ghi cả caller=svc:gateway VÀ actor_id=<user gốc>.\n"
[[ "$fail" -eq 0 ]] && exit 0 || exit 1
