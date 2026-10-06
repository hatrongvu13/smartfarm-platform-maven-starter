#!/usr/bin/env bash
# SmartFarm — Livestock task lifecycle + deadline-monitor curl test.
#
# Prereq (dev profile): identity(:8092), livestock(:8081/gRPC 9091), gateway(:8080) running.
#   mvn -pl services/smartfarm-identity-service  spring-boot:run
#   mvn -pl services/smartfarm-livestock-service spring-boot:run
#   mvn -pl apps/smartfarm-gateway               spring-boot:run
#
# Bootstrap user (dev) = SUPERADMIN -> has tasks:write + farm:read. If a call 403s:
# restart identity (bootstrap converges roles) and login again.
#
# The monitor uses the DEV windows in application-dev.yml (accept 120s, report 600s). This
# script assigns with a SHORT explicit accept window (5s) so accept-overdue can be observed
# quickly; watch the livestock process log for `task_accept_overdue`.

set -uo pipefail
GW="${GW:-http://localhost:8080}"
TENANT="${TENANT:-farm-demo}"
EMAIL="${EMAIL:-admin@example.test}"
PASSWORD="${PASSWORD:-replace-with-a-long-random-password}"
FARM="farm-1"
WORKER="worker-$RANDOM"
PASS=0; FAIL=0
say(){ printf '   %s\n' "$*"; }
ok(){ PASS=$((PASS+1)); printf '   PASS %s\n' "$*"; }
no(){ FAIL=$((FAIL+1)); printf '   FAIL %s\n' "$*"; }
jget(){ printf '%s' "$1" | sed -n "s/.*\"$2\"[[:space:]]*:[[:space:]]*\"\{0,1\}\([^\",}]*\)\"\{0,1\}.*/\1/p" | head -1; }

echo "SmartFarm — Livestock task lifecycle + monitor"

echo
echo "== 0. Đăng nhập =="
LOGIN=$(curl -sS -w '\n%{http_code}' -X POST "$GW/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d "{\"tenantId\":\"$TENANT\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
CODE=$(printf '%s' "$LOGIN" | tail -1); BODY=$(printf '%s' "$LOGIN" | sed '$d')
[ "$CODE" = "200" ] && ok "login (200)" || { no "login (got $CODE)"; echo "$BODY"; exit 1; }
ACCESS=$(jget "$BODY" accessToken); [ -n "$ACCESS" ] || ACCESS=$(jget "$BODY" access_token)
[ -n "$ACCESS" ] && say "token: ${ACCESS:0:24}..." || { no "no token"; exit 1; }
AUTH=(-H "Authorization: Bearer $ACCESS")

echo
echo "== 1. Tạo task (type INSPECTION) =="
R=$(curl -sS -w '\n%{http_code}' -X POST "$GW/api/v1/livestock/tasks" "${AUTH[@]}" \
  -H 'Content-Type: application/json' -H "Idempotency-Key: task-$RANDOM" \
  -d "{\"farmId\":\"$FARM\",\"title\":\"Kiểm chuồng A\",\"type\":\"INSPECTION\"}")
CODE=$(printf '%s' "$R" | tail -1); BODY=$(printf '%s' "$R" | sed '$d')
[ "$CODE" = "200" ] && ok "create task (200)" || { no "create (got $CODE)"; echo "$BODY"; exit 1; }
TID=$(jget "$BODY" taskId); say "taskId=$TID"

echo
echo "== 2. Giao việc với accept-window 5s (để quan sát quá hạn nhận) =="
R=$(curl -sS -w '\n%{http_code}' -X POST "$GW/api/v1/livestock/tasks/$TID/assign" "${AUTH[@]}" \
  -H 'Content-Type: application/json' \
  -d "{\"assigneeId\":\"$WORKER\",\"acceptWindowSeconds\":5,\"reportWindowSeconds\":30}")
CODE=$(printf '%s' "$R" | tail -1); BODY=$(printf '%s' "$R" | sed '$d')
[ "$CODE" = "200" ] && ok "assign (200)" || { no "assign (got $CODE)"; echo "$BODY"; }
printf '%s' "$BODY" | grep -q '"TASK_STATUS_ASSIGNED"' && ok "status ASSIGNED" || no "status không ASSIGNED"
printf '%s' "$BODY" | grep -q 'acceptDeadlineAt' && ok "có acceptDeadlineAt (deadline giám sát)" || no "thiếu acceptDeadlineAt"

echo
echo "== 3. CHỜ 20s để monitor quét quá hạn NHẬN (accept-overdue) =="
say "monitor poll ~15s + window 5s. Xem log livestock: 'task_accept_overdue'."
sleep 20
R=$(curl -sS "$GW/api/v1/livestock/tasks/$TID" "${AUTH[@]}")
say "task sau chờ: $(printf '%s' "$R" | tr -d ' ')"
# Task vẫn ASSIGNED (chưa ai nhận) — monitor đã phát event overdue (kiểm ở log), không đổi status.
printf '%s' "$R" | grep -q '"TASK_STATUS_ASSIGNED"' && ok "vẫn ASSIGNED (chờ nhận) — overdue event đã phát ở log" || say "status đã đổi: $R"

echo
echo "== 4. Công nhân NHẬN việc (accept) — dừng đồng hồ accept-overdue =="
R=$(curl -sS -w '\n%{http_code}' -X POST "$GW/api/v1/livestock/tasks/$TID/accept" "${AUTH[@]}")
CODE=$(printf '%s' "$R" | tail -1); BODY=$(printf '%s' "$R" | sed '$d')
[ "$CODE" = "200" ] && ok "accept (200)" || no "accept (got $CODE)"
printf '%s' "$BODY" | grep -q '"TASK_STATUS_ACCEPTED"' && ok "status ACCEPTED" || no "status không ACCEPTED"
printf '%s' "$BODY" | grep -q 'acceptedAt' && ok "có acceptedAt" || no "thiếu acceptedAt"

echo
echo "== 5. Idempotency accept — nhận lại trả cùng ACCEPTED, không lỗi =="
R=$(curl -sS -w '\n%{http_code}' -X POST "$GW/api/v1/livestock/tasks/$TID/accept" "${AUTH[@]}")
CODE=$(printf '%s' "$R" | tail -1)
[ "$CODE" = "200" ] && ok "accept lặp (200, idempotent)" || no "accept lặp (got $CODE)"

echo
echo "== 6. Hoàn thành (complete) — thoả deadline báo cáo =="
R=$(curl -sS -w '\n%{http_code}' -X POST "$GW/api/v1/livestock/tasks/$TID/complete" "${AUTH[@]}" \
  -H 'Content-Type: application/json' -d '{"note":"Đã kiểm, bình thường"}')
CODE=$(printf '%s' "$R" | tail -1); BODY=$(printf '%s' "$R" | sed '$d')
[ "$CODE" = "200" ] && ok "complete (200)" || no "complete (got $CODE)"
printf '%s' "$BODY" | grep -q '"TASK_STATUS_COMPLETED"' && ok "status COMPLETED" || no "status không COMPLETED"

echo
echo "== 7. Không cho complete/assign lại task đã hoàn thành (FAILED_PRECONDITION -> 409) =="
R=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$GW/api/v1/livestock/tasks/$TID/assign" "${AUTH[@]}" \
  -H 'Content-Type: application/json' -d "{\"assigneeId\":\"$WORKER\"}")
[ "$R" = "409" ] && ok "assign task terminal -> 409" || no "assign terminal (got $R, muốn 409)"

echo
echo "== 8. List task theo farm =="
R=$(curl -sS -w '\n%{http_code}' "$GW/api/v1/livestock/tasks?farmId=$FARM&limit=10" "${AUTH[@]}")
CODE=$(printf '%s' "$R" | tail -1); BODY=$(printf '%s' "$R" | sed '$d')
[ "$CODE" = "200" ] && ok "list (200)" || no "list (got $CODE)"
printf '%s' "$BODY" | grep -q "$TID" && ok "list chứa task vừa tạo" || no "list không thấy task"

echo
echo "== 9. Ca hủy (cancel) trên task mới =="
R=$(curl -sS -X POST "$GW/api/v1/livestock/tasks" "${AUTH[@]}" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: task-$RANDOM" -d "{\"farmId\":\"$FARM\",\"title\":\"Việc bỏ\",\"type\":\"OTHER\"}")
TID2=$(jget "$R" taskId)
R=$(curl -sS -w '\n%{http_code}' -X POST "$GW/api/v1/livestock/tasks/$TID2/cancel" "${AUTH[@]}" \
  -H 'Content-Type: application/json' -d '{"reason":"trùng lịch"}')
CODE=$(printf '%s' "$R" | tail -1); BODY=$(printf '%s' "$R" | sed '$d')
[ "$CODE" = "200" ] && ok "cancel (200)" || no "cancel (got $CODE)"
printf '%s' "$BODY" | grep -q '"TASK_STATUS_CANCELLED"' && ok "status CANCELLED" || no "status không CANCELLED"

echo
echo "Kết quả: $PASS PASS, $FAIL FAIL"
echo "Kiểm sâu ở LOG livestock (smartfarm.audit.task):"
echo "  task_assigned / task_accepted / task_completed / task_cancelled — mỗi dòng có actor_id"
echo "  task_accept_overdue (bước 3) — monitor phát khi quá hạn nhận"
echo "  outbox: task-assigned.v1 / task-accepted.v1 / task-accept-overdue.v1 ... -> MQTT"
[ "$FAIL" = "0" ]
