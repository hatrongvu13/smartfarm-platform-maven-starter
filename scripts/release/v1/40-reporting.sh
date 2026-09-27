#!/usr/bin/env bash
# 40 — Reporting flow: request export -> worker renders -> COMPLETED -> download location.
set -uo pipefail
source "$(dirname "$0")/_lib.sh"
FARM="farm-1"

echo "SmartFarm v1 — Reporting flow"
login || exit 1

hdr "1. RequestExport (CSV báo cáo tồn kho) -> QUEUED"
R=$(curl -sS -w '\n%{http_code}' -X POST "$GW/api/v1/reports" "${AUTH[@]}" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: rpt-$RANDOM" -d "{\"farmId\":\"$FARM\",\"type\":\"INVENTORY\",\"format\":\"CSV\"}")
CODE=$(printf '%s' "$R" | tail -1); BODY=$(printf '%s' "$R" | sed '$d')
[ "$CODE" = "200" ] && ok "request export (200)" || { no "request export (got $CODE)"; echo "$BODY"; exit 1; }
JID=$(jget "$BODY" jobId); say "jobId=$JID status=$(jget "$BODY" status)"

hdr "2. Chờ worker xử lý (poll 5s) -> COMPLETED"
DONE=0
for i in $(seq 1 8); do   # 8 * 4s = 32s
  sleep 4
  R=$(curl -sS "$GW/api/v1/reports/$JID" "${AUTH[@]}")
  ST=$(jget "$R" status)
  if [ "$ST" = "EXPORT_STATUS_COMPLETED" ]; then DONE=1; say "COMPLETED sau $((i*4))s"; break; fi
  if [ "$ST" = "EXPORT_STATUS_FAILED" ]; then no "job FAILED: $R"; break; fi
done
[ "$DONE" = "1" ] && ok "export job COMPLETED" || no "job chưa COMPLETED sau 32s (kiểm worker enabled)"

hdr "3. GetDownloadLocation -> url + contentType + expiresAt"
if [ "$DONE" = "1" ]; then
  R=$(curl -sS -w '\n%{http_code}' "$GW/api/v1/reports/$JID/download" "${AUTH[@]}")
  CODE=$(printf '%s' "$R" | tail -1); BODY=$(printf '%s' "$R" | sed '$d')
  [ "$CODE" = "200" ] && ok "download location (200)" || no "download (got $CODE)"
  printf '%s' "$BODY" | grep -q 'file://' && ok "trả url file (starter: local path)" || say "url: $BODY"
  printf '%s' "$BODY" | grep -q 'text/csv' && ok "contentType text/csv" || say "contentType khác: $BODY"
fi

hdr "4. ListExportJobs theo farm"
R=$(curl -sS -w '\n%{http_code}' "$GW/api/v1/reports?farmId=$FARM" "${AUTH[@]}")
CODE=$(printf '%s' "$R" | tail -1); BODY=$(printf '%s' "$R" | sed '$d')
[ "$CODE" = "200" ] && ok "list jobs (200)" || no "list jobs (got $CODE)"
printf '%s' "$BODY" | grep -q "$JID" && ok "list chứa job vừa tạo" || no "list thiếu job"

summary "reporting"
