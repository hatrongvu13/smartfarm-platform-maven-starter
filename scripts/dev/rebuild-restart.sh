#!/usr/bin/env bash
# SmartFarm dev: build proto+services COHERENTLY, then hard-restart every service
# from the terminal (NOT IntelliJ) so no stale JVM keeps an old gRPC stub bound.
#
# Root cause this script removes: a service JVM binds its gRPC RPC set ONCE at boot
# (bindService). If it started before the proto/service rebuild landed, or a previous
# `spring-boot:run` JVM was never fully killed, the live server is missing new RPCs
# (e.g. FarmFinanceService/RecordExpense -> UNIMPLEMENTED) even though the jar on disk
# and the compiled impl are correct. We build FIRST, wait, then kill-by-port + relaunch.
#
# Usage:
#   bash scripts/dev/rebuild-restart.sh              # build all + restart all
#   bash scripts/dev/rebuild-restart.sh finance order gateway   # only these
#   SKIP_BUILD=1 bash scripts/dev/rebuild-restart.sh # just hard-restart (no mvn)
#
# Requires: mvn on PATH, profile `dev`. Postgres + (optional) mosquitto up already.
set -euo pipefail
cd "$(dirname "$0")/../.."          # repo root
ROOT="$(pwd)"
LOG_DIR="${KIROCREW_SCRATCH:-/tmp}/smartfarm-dev-logs"
mkdir -p "$LOG_DIR"

# service key -> "module|port"
svc_module() { case "$1" in
  identity)  echo "services/smartfarm-identity-service|8092";;
  livestock) echo "services/smartfarm-livestock-service|9091";;
  inventory) echo "services/smartfarm-inventory-service|9093";;
  finance)   echo "services/smartfarm-finance-service|9094";;
  order)     echo "services/smartfarm-order-service|9095";;
  reporting) echo "services/smartfarm-reporting-service|9096";;
  gateway)   echo "apps/smartfarm-gateway|8080";;
  *) echo "";;
esac; }

ALL=(identity livestock inventory finance order reporting gateway)
TARGETS=("$@"); [ ${#TARGETS[@]} -eq 0 ] && TARGETS=("${ALL[@]}")

echo "==> targets: ${TARGETS[*]}"

# 1) BUILD FIRST (coherent: proto is rebuilt and installed before anything starts)
if [ "${SKIP_BUILD:-0}" != "1" ]; then
  MODS="libs/smartfarm-proto"
  for s in "${TARGETS[@]}"; do
    info="$(svc_module "$s")"; [ -z "$info" ] && { echo "!! unknown service '$s'"; exit 2; }
    MODS="$MODS,${info%%|*}"
  done
  echo "==> mvn clean install (offline) : $MODS"
  mvn -o -q -pl "$MODS" -am clean install -DskipTests
  echo "==> build OK"
fi

# 2) KILL any JVM currently bound to each target port (stale-process guard)
kill_port() {
  local port="$1" pids
  pids="$(lsof -nP -iTCP:"$port" -sTCP:LISTEN 2>/dev/null | awk '/LISTEN/{print $2}' | sort -u)"
  if [ -n "$pids" ]; then
    echo "   killing PID(s) on :$port -> $pids"
    # shellcheck disable=SC2086
    kill $pids 2>/dev/null || true
    sleep 2
    pids="$(lsof -nP -iTCP:"$port" -sTCP:LISTEN 2>/dev/null | awk '/LISTEN/{print $2}' | sort -u)"
    # shellcheck disable=SC2086
    [ -n "$pids" ] && { echo "   force-killing $pids"; kill -9 $pids 2>/dev/null || true; sleep 1; }
  fi
}
echo "==> stopping stale processes"
for s in "${TARGETS[@]}"; do info="$(svc_module "$s")"; kill_port "${info##*|}"; done

# 3) RELAUNCH in dependency order (identity first, gateway last), background, logged
launch() {
  local s="$1" info module port; info="$(svc_module "$s")"; module="${info%%|*}"; port="${info##*|}"
  local log="$LOG_DIR/$s.log"
  echo "   starting $s ($module) -> log $log"
  ( cd "$ROOT" && nohup mvn -o -pl "$module" spring-boot:run -Dspring-boot.run.profiles=dev >"$log" 2>&1 & )
  # wait up to 90s for "Started" in the log
  for _ in $(seq 1 45); do
    grep -qE 'Started .* in [0-9.]+ s|Netty started|gRPC Server started' "$log" 2>/dev/null && { echo "   $s UP"; return 0; }
    sleep 2
  done
  echo "   !! $s did not report Started within 90s — see $log"
  return 1
}
ORDERED=(identity livestock inventory finance order reporting gateway)
echo "==> launching (ordered)"
for s in "${ORDERED[@]}"; do
  for t in "${TARGETS[@]}"; do [ "$s" = "$t" ] && launch "$s"; done
done

echo
echo "==> done. Logs in $LOG_DIR"
echo "    Now run:  bash scripts/release/v1/run-all.sh"
