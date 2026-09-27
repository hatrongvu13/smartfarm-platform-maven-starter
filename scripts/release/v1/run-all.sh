#!/usr/bin/env bash
# run-all — chạy toàn bộ bộ test release v1 theo thứ tự. Dừng sớm nếu preflight fail.
#
# Prereq: 5 service + gateway chạy profile dev (identity, livestock, inventory, finance,
# order, reporting, gateway). Xem docs/GATEWAY-API-V1.md phần "Khởi động".
#
# FAST_ONLY=1 ./run-all.sh  -> bỏ qua các bước chờ dài (generator 90s).
set -uo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"

echo "########################################"
echo "# SmartFarm v1 — Release test suite"
echo "########################################"

"$DIR/00-preflight.sh" || { echo ">> Preflight FAILED — dừng. Khởi động đủ service rồi chạy lại."; exit 1; }

RC=0
for s in 10-auth-rbac 20-livestock-flow 30-order-saga 40-reporting 50-reporting-render; do
  echo
  echo "########## $s ##########"
  "$DIR/$s.sh" || RC=1
done

echo
if [ "$RC" = "0" ]; then echo ">> TẤT CẢ LUỒNG NGHIỆP VỤ v1: PASS"; else echo ">> CÓ LUỒNG FAIL — xem log ở trên"; fi
exit $RC
