#!/usr/bin/env bash
# SmartFarm — Livestock animal registry + schedule generator curl test.
#
# Prereq (dev profile): identity(:8092), livestock(:8081/gRPC 9091), gateway(:8080) running.
#   mvn -pl services/smartfarm-identity-service  spring-boot:run
#   mvn -pl services/smartfarm-livestock-service spring-boot:run
#   mvn -pl apps/smartfarm-gateway               spring-boot:run
#
# Bootstrap user (dev) = SUPERADMIN -> has tasks:write + farm:read. If a call 403s:
# restart identity (bootstrap converges roles) and login again.
#
# Section 2 creates a cron schedule that fires every minute and WAITS up to ~90s for the
# generator (dev poll 15s) to materialise a task. Watch the livestock log for
# `schedule_task_created`. Set FAST_ONLY=1 to skip the wait (schedule create/list only).

set -uo pipefail
GW="${GW:-http://localhost:8080}"
TENANT="${TENANT:-farm-demo}"
EMAIL="${EMAIL:-admin@example.test}"
PASSWORD="${PASSWORD:-replace-with-a-long-random-password}"
FARM="farm-1"
FAST_ONLY="${FAST_ONLY:-0}"
PASS=0; FAIL=0
say(){ printf '   %s\n' "$*"; }
ok(){ PASS=$((PASS+1)); printf '   PASS %s\n' "$*"; }
no(){ FAIL=$((FAIL+1)); printf '   FAIL %s\n' "$*"; }
jget(){ printf '%s' "$1" | sed -n "s/.*\"$2\"[[:space:]]*:[[:space:]]*\"\{0,1\}\([^\",}]*\)\"\{0,1\}.*/\1/p" | head -1; }

echo "SmartFarm — Animal registry + schedule generator"

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
echo "== 1. ANIMAL — đăng ký + idempotent + get + list =="
TAG="VN-$RANDOM"
R=$(curl -sS -w '\n%{http_code}' -X POST "$GW/api/v1/livestock/animals" "${AUTH[@]}" \
  -H 'Content-Type: application/json' \
  -d "{\"farmId\":\"$FARM\",\"tagCode\":\"$TAG\",\"species\":\"pig\",\"batchId\":\"batch-A\"}")
CODE=$(printf '%s' "$R" | tail -1); BODY=$(printf '%s' "$R" | sed '$d')
[ "$CODE" = "200" ] && ok "register animal (200)" || { no "register (got $CODE)"; echo "$BODY"; exit 1; }
AID=$(jget "$BODY" animalId); say "animalId=$AID tag=$TAG"
printf '%s' "$BODY" | grep -q '"ANIMAL_STATUS_ACTIVE"' && ok "status mặc định ACTIVE" || no "status không ACTIVE"

# Idempotent: cùng tag trong farm -> cùng animalId, không tạo bản ghi mới.
R2=$(curl -sS -X POST "$GW/api/v1/livestock/animals" "${AUTH[@]}" -H 'Content-Type: application/json' \
  -d "{\"farmId\":\"$FARM\",\"tagCode\":\"$TAG\",\"species\":\"pig\"}")
AID2=$(jget "$R2" animalId)
[ "$AID" = "$AID2" ] && ok "idempotent theo tag (cùng animalId)" || no "tag idempotency (got $AID2, muốn $AID)"

# Get + list
R=$(curl -sS -o /dev/null -w '%{http_code}' "$GW/api/v1/livestock/animals/$AID" "${AUTH[@]}")
[ "$R" = "200" ] && ok "get animal (200)" || no "get animal (got $R)"
R=$(curl -sS "$GW/api/v1/livestock/animals?farmId=$FARM&batchId=batch-A" "${AUTH[@]}")
printf '%s' "$R" | grep -q "$AID" && ok "list animals chứa animal vừa tạo" || no "list không thấy animal"

echo
echo "== 2. SCHEDULE — validate cron sai bị từ chối =="
R=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$GW/api/v1/livestock/schedules" "${AUTH[@]}" \
  -H 'Content-Type: application/json' \
  -d "{\"farmId\":\"$FARM\",\"title\":\"cron xấu\",\"cronExpression\":\"khong-phai-cron\",\"timeZone\":\"Asia/Ho_Chi_Minh\"}")
[ "$R" = "400" ] && ok "cron sai -> 400 (validate)" || no "cron sai (got $R, muốn 400)"

R=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$GW/api/v1/livestock/schedules" "${AUTH[@]}" \
  -H 'Content-Type: application/json' \
  -d "{\"farmId\":\"$FARM\",\"title\":\"tz xấu\",\"cronExpression\":\"0 * * * * *\",\"timeZone\":\"Khong/Ton_Tai\"}")
[ "$R" = "400" ] && ok "timezone sai -> 400 (validate)" || no "tz sai (got $R, muốn 400)"

echo
echo "== 3. SCHEDULE — tạo lịch cron mỗi phút =="
R=$(curl -sS -w '\n%{http_code}' -X POST "$GW/api/v1/livestock/schedules" "${AUTH[@]}" \
  -H 'Content-Type: application/json' \
  -d "{\"farmId\":\"$FARM\",\"title\":\"Kiểm chuồng định kỳ\",\"type\":\"INSPECTION\",\"cronExpression\":\"0 * * * * *\",\"timeZone\":\"Asia/Ho_Chi_Minh\"}")
CODE=$(printf '%s' "$R" | tail -1); BODY=$(printf '%s' "$R" | sed '$d')
[ "$CODE" = "200" ] && ok "create schedule (200)" || { no "create schedule (got $CODE)"; echo "$BODY"; }
SID=$(jget "$BODY" scheduleId); say "scheduleId=$SID"
printf '%s' "$BODY" | grep -q 'nextRunAt' && ok "có nextRunAt (đã tính từ cron+tz)" || no "thiếu nextRunAt"

R=$(curl -sS "$GW/api/v1/livestock/schedules?farmId=$FARM" "${AUTH[@]}")
printf '%s' "$R" | grep -q "$SID" && ok "list schedules chứa lịch vừa tạo" || no "list không thấy schedule"

if [ "$FAST_ONLY" = "1" ]; then
  echo; echo "FAST_ONLY=1 -> bỏ qua chờ generator."; echo "Kết quả: $PASS PASS, $FAIL FAIL"; [ "$FAIL" = "0" ]; exit $?
fi

echo
echo "== 4. GENERATOR — chờ tự sinh task từ lịch (cron mỗi phút, poll 15s) =="
say "Đếm task trước, rồi chờ tối đa 90s cho generator sinh task mới (log: schedule_task_created)."
BEFORE=$(curl -sS "$GW/api/v1/livestock/tasks?farmId=$FARM&limit=200" "${AUTH[@]}" | grep -o '"taskId"' | wc -l | tr -d ' ')
say "task hiện có: $BEFORE"
FOUND=0
for i in $(seq 1 18); do   # 18 * 5s = 90s
  sleep 5
  # Tìm task do lịch này sinh: dueAt là ô cron, title trùng. Đơn giản: đếm task tăng lên.
  NOW=$(curl -sS "$GW/api/v1/livestock/tasks?farmId=$FARM&limit=200" "${AUTH[@]}" | grep -o '"taskId"' | wc -l | tr -d ' ')
  if [ "$NOW" -gt "$BEFORE" ]; then FOUND=1; say "task tăng $BEFORE -> $NOW sau $((i*5))s"; break; fi
done
[ "$FOUND" = "1" ] && ok "generator tự sinh task từ lịch cron" || no "sau 90s chưa thấy task mới (kiểm log schedule_task_created + generator enabled)"

# Kiểm task sinh ra mang đúng title/type của lịch
R=$(curl -sS "$GW/api/v1/livestock/tasks?farmId=$FARM&limit=200" "${AUTH[@]}")
printf '%s' "$R" | grep -q 'Kiểm chuồng định kỳ' && ok "task sinh ra mang title của lịch" || say "chưa thấy title lịch (có thể chờ thêm 1 phút)"

echo
echo "Kết quả: $PASS PASS, $FAIL FAIL"
echo "Kiểm sâu ở LOG livestock (smartfarm.audit.schedule):"
echo "  schedule_created — khi tạo lịch (có cron, tz, next_run)"
echo "  schedule_task_created — mỗi lần generator sinh task theo cron"
echo "  schedule_generated count=N — tổng kết mỗi chu kỳ quét"
echo "Idempotency: chạy lại generator cùng ô thời gian KHÔNG tạo task trùng (key sched:<id>:<slot>)."
[ "$FAIL" = "0" ]
