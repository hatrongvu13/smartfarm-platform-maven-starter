#!/usr/bin/env bash
# 20 — Livestock business flow: task lifecycle + deadline monitor + animal + schedule generator.
set -uo pipefail
source "$(dirname "$0")/_lib.sh"
FARM="farm-1"; WORKER="worker-$RANDOM"

echo "SmartFarm v1 — Livestock flow"
login || exit 1

hdr "1. Task lifecycle: create -> assign -> accept -> complete"
R=$(curl -sS -X POST "$GW/api/v1/livestock/tasks" "${AUTH[@]}" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: t-$RANDOM" -d "{\"farmId\":\"$FARM\",\"title\":\"Kiểm chuồng A\",\"type\":\"INSPECTION\"}")
TID=$(jget "$R" taskId); [ -n "$TID" ] && ok "create task ($TID)" || { no "create task"; echo "$R"; }
R=$(curl -sS -X POST "$GW/api/v1/livestock/tasks/$TID/assign" "${AUTH[@]}" -H 'Content-Type: application/json' \
  -d "{\"assigneeId\":\"$WORKER\",\"acceptWindowSeconds\":5,\"reportWindowSeconds\":30}")
printf '%s' "$R" | grep -q 'TASK_STATUS_ASSIGNED' && ok "assign -> ASSIGNED (có deadline)" || no "assign"
R=$(curl -sS -X POST "$GW/api/v1/livestock/tasks/$TID/accept" "${AUTH[@]}")
printf '%s' "$R" | grep -q 'TASK_STATUS_ACCEPTED' && ok "accept -> ACCEPTED" || no "accept"
R=$(curl -sS -X POST "$GW/api/v1/livestock/tasks/$TID/complete" "${AUTH[@]}" -H 'Content-Type: application/json' -d '{"note":"ok"}')
printf '%s' "$R" | grep -q 'TASK_STATUS_COMPLETED' && ok "complete -> COMPLETED" || no "complete"

hdr "2. Monitor quá hạn nhận (accept-overdue)"
R=$(curl -sS -X POST "$GW/api/v1/livestock/tasks" "${AUTH[@]}" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: t-$RANDOM" -d "{\"farmId\":\"$FARM\",\"title\":\"Việc không nhận\",\"type\":\"OTHER\"}")
TID2=$(jget "$R" taskId)
curl -sS -o /dev/null -X POST "$GW/api/v1/livestock/tasks/$TID2/assign" "${AUTH[@]}" -H 'Content-Type: application/json' \
  -d "{\"assigneeId\":\"$WORKER\",\"acceptWindowSeconds\":3,\"reportWindowSeconds\":300}"
say "chờ 25s cho monitor (poll 15s + window 3s) phát accept-overdue -> log task_accept_overdue"
sleep 25
R=$(curl -sS "$GW/api/v1/livestock/tasks/$TID2" "${AUTH[@]}")
printf '%s' "$R" | grep -q 'TASK_STATUS_ASSIGNED' && ok "task chưa nhận vẫn ASSIGNED (overdue event ở log)" || say "trạng thái: $R"

hdr "3. Animal registry (idempotent theo tag)"
TAG="VN-$RANDOM"
R=$(curl -sS -X POST "$GW/api/v1/livestock/animals" "${AUTH[@]}" -H 'Content-Type: application/json' \
  -d "{\"farmId\":\"$FARM\",\"tagCode\":\"$TAG\",\"species\":\"pig\"}")
AID=$(jget "$R" animalId); [ -n "$AID" ] && ok "register animal ($AID)" || no "register animal"
R2=$(curl -sS -X POST "$GW/api/v1/livestock/animals" "${AUTH[@]}" -H 'Content-Type: application/json' \
  -d "{\"farmId\":\"$FARM\",\"tagCode\":\"$TAG\",\"species\":\"pig\"}")
[ "$(jget "$R2" animalId)" = "$AID" ] && ok "idempotent theo tag" || no "tag không idempotent"

hdr "4. Schedule generator (cron mỗi phút tự sinh task)"
R=$(curl -sS -X POST "$GW/api/v1/livestock/schedules" "${AUTH[@]}" -H 'Content-Type: application/json' \
  -d "{\"farmId\":\"$FARM\",\"title\":\"Định kỳ mỗi phút\",\"type\":\"INSPECTION\",\"cronExpression\":\"0 * * * * *\",\"timeZone\":\"Asia/Ho_Chi_Minh\"}")
printf '%s' "$R" | grep -q 'nextRunAt' && ok "create schedule (có nextRunAt)" || no "create schedule"
if [ "${FAST_ONLY:-0}" = "1" ]; then say "FAST_ONLY=1 -> bỏ qua chờ generator"; else
  BEFORE=$(curl -sS "$GW/api/v1/livestock/tasks?farmId=$FARM&limit=200" "${AUTH[@]}" | grep -o '"taskId"' | wc -l | tr -d ' ')
  say "chờ tối đa 90s cho generator sinh task (log schedule_task_created)"
  FOUND=0
  for i in $(seq 1 18); do sleep 5
    NOW=$(curl -sS "$GW/api/v1/livestock/tasks?farmId=$FARM&limit=200" "${AUTH[@]}" | grep -o '"taskId"' | wc -l | tr -d ' ')
    [ "$NOW" -gt "$BEFORE" ] && { FOUND=1; break; }
  done
  [ "$FOUND" = "1" ] && ok "generator tự sinh task theo cron" || no "sau 90s chưa thấy task mới"
fi

summary "livestock-flow"
