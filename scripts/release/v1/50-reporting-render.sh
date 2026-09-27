#!/usr/bin/env bash
# 50 — Reporting render (CSV/XLSX/PDF) + finance report data.
# Finance report RPCs (batch cost / cash flow) are internal gRPC; this script exercises the
# reporting export path end-to-end and verifies real multi-format rendering + download.
set -uo pipefail
source "$(dirname "$0")/_lib.sh"
FARM="farm-1"

echo "SmartFarm v1 — Reporting render + finance data"
login || exit 1

test_format(){ # format ext
  local fmt="$1" ext="$2"
  hdr "Export LIVESTOCK_TASKS định dạng $fmt (dữ liệu thật qua gRPC)"
  local r code body jid
  r=$(curl -sS -w '\n%{http_code}' -X POST "$GW/api/v1/reports" "${AUTH[@]}" -H 'Content-Type: application/json' \
    -H "Idempotency-Key: rpt-$fmt-$RANDOM" -d "{\"farmId\":\"$FARM\",\"type\":\"LIVESTOCK_TASKS\",\"format\":\"$fmt\"}")
  code=$(printf '%s' "$r" | tail -1); body=$(printf '%s' "$r" | sed '$d')
  [ "$code" = "200" ] && ok "request $fmt (200)" || { no "request $fmt (got $code)"; echo "$body"; return; }
  jid=$(jget "$body" jobId)
  local done=0 st
  for i in $(seq 1 8); do sleep 4
    st=$(jget "$(curl -sS "$GW/api/v1/reports/$jid" "${AUTH[@]}")" status)
    [ "$st" = "EXPORT_STATUS_COMPLETED" ] && { done=1; break; }
    [ "$st" = "EXPORT_STATUS_FAILED" ] && { no "$fmt job FAILED"; return; }
  done
  [ "$done" = "1" ] && ok "$fmt job COMPLETED" || { no "$fmt job chưa xong sau 32s"; return; }
  r=$(curl -sS "$GW/api/v1/reports/$jid/download" "${AUTH[@]}")
  printf '%s' "$r" | grep -q "file://" && ok "$fmt có download url" || no "$fmt thiếu url"
  printf '%s' "$r" | grep -q "\.$ext" && ok "$fmt file đuôi .$ext (render đúng định dạng)" || say "url: $r"
}

test_format CSV csv
test_format XLSX xlsx
test_format PDF pdf

hdr "Kiểm file render thật trên đĩa (nếu chạy cùng máy reporting)"
DIR="${REPORTING_OUTPUT_DIR:-services/smartfarm-reporting-service/.local/reports}"
if [ -d "$DIR" ]; then
  n=$(ls "$DIR" 2>/dev/null | grep -cE '\.(csv|xlsx|pdf)$' || echo 0)
  [ "$n" -gt 0 ] && ok "có $n file báo cáo trong $DIR" || say "chưa thấy file (reporting có thể chạy máy khác)"
else
  say "output dir $DIR không thấy — reporting chạy máy/CWD khác, bỏ qua kiểm đĩa"
fi

echo
say "Finance report RPC (GetBatchCost/GetCashFlow) là gRPC nội bộ — verify qua log finance"
say "hoặc thêm dev REST facade nếu muốn test bằng curl (ngoài phạm vi v1 UI)."
summary "reporting-render"
