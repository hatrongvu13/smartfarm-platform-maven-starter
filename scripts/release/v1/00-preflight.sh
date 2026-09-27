#!/usr/bin/env bash
# 00 — Preflight: verify all v1 services are up before running the flows.
set -uo pipefail
source "$(dirname "$0")/_lib.sh"

echo "SmartFarm v1 — Preflight"

hdr "Cổng gRPC nội bộ (loopback)"
check_port(){ # name host port
  if command -v nc >/dev/null 2>&1; then
    nc -z -w2 "$2" "$3" >/dev/null 2>&1 && ok "$1 ($2:$3) OPEN" || no "$1 ($2:$3) CLOSED"
  else
    (exec 3<>"/dev/tcp/$2/$3") >/dev/null 2>&1 && ok "$1 ($2:$3) OPEN" || no "$1 ($2:$3) CLOSED"
  fi
}
check_port identity   localhost 8092
check_port livestock  localhost 9091
check_port inventory  localhost 9093
check_port finance    localhost 9094
check_port order      localhost 9095
check_port reporting  localhost 9096

hdr "Gateway ingress"
CODE=$(curl -sS -o /dev/null -w '%{http_code}' "$GW/actuator/health" 2>/dev/null || echo 000)
[ "$CODE" = "200" ] || CODE=$(curl -sS -o /dev/null -w '%{http_code}' "$GW/v3/api-docs" 2>/dev/null || echo 000)
[ "$CODE" != "000" ] && ok "gateway $GW reachable (HTTP $CODE)" || no "gateway $GW unreachable"

hdr "Đăng nhập"
login || exit 1

summary "preflight"
