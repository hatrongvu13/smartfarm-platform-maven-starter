#!/usr/bin/env bash
set -uo pipefail
MODE="${1:-static}"
case "$MODE" in static|build|runtime|all|strict) ;; *) echo "Usage: $0 {static|build|runtime|all|strict}"; exit 2;; esac
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
cd "$ROOT"
REPORT_DIR="${ORDER_VALIDATION_REPORT_DIR:-build}"
REPORT="$REPORT_DIR/order-production-validation-report.md"
mkdir -p "$REPORT_DIR"
PASS=0; FAIL=0; SKIP=0
COMMIT="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
NOW="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
ORDER_URL="${ORDER_URL:-http://localhost:8085}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
STRICT=0; [ "$MODE" = strict ] && STRICT=1

redact(){ sed -E 's/((PASSWORD|SECRET|TOKEN|KEY|CREDENTIAL)[A-Za-z0-9_]*=)[^[:space:]]*/\1[REDACTED]/Ig'; }
record(){ printf '| %s | %s | %s |\n' "$1" "$2" "${3//|/\\|}" >> "$REPORT"; }
pass(){ PASS=$((PASS+1)); echo "PASS $1"; record "$1" PASS "${2:-}"; }
fail(){ FAIL=$((FAIL+1)); echo "FAIL $1: ${2:-}" >&2; record "$1" FAIL "${2:-}"; }
skip(){ SKIP=$((SKIP+1)); echo "SKIP $1: ${2:-}"; record "$1" SKIP "${2:-}"; }
contains(){ grep -Fq "$2" "$1"; }
not_contains(){ ! grep -Fq "$2" "$1"; }
http_code(){ curl -k -sS -o "$REPORT_DIR/.validation-body" -w '%{http_code}' --max-time 10 "$1" 2>/dev/null || echo 000; }
json_status(){ python3 - "$REPORT_DIR/.validation-body" <<'PYJ'
import json,sys
try: print(json.load(open(sys.argv[1],encoding='utf-8')).get('status',''))
except Exception: print('')
PYJ
}
metric_value(){ awk -v n="$1" '$1==n {print $2; exit}' "$REPORT_DIR/.validation-body"; }

cat > "$REPORT" <<EOF
# Order production validation report

- Time (UTC): $NOW
- Commit: $COMMIT
- Mode: $MODE

| Check | Result | Evidence |
|---|---|---|
EOF

static_checks(){
  contains Dockerfile 'USER 1001:1001' && pass 'container.non-root' 'UID/GID 1001' || fail 'container.non-root' 'USER 1001:1001 missing'
  contains Dockerfile 'ENTRYPOINT ["java","-jar","/app/app.jar"]' && pass 'container.exec-entrypoint' || fail 'container.exec-entrypoint'
  contains Dockerfile 'HEALTHCHECK' && contains Dockerfile '/actuator/health/readiness' && pass 'container.healthcheck' || fail 'container.healthcheck'
  contains Dockerfile 'MaxRAMPercentage' && contains Dockerfile 'ExitOnOutOfMemoryError' && pass 'container.jvm-runtime' || fail 'container.jvm-runtime'
  contains Dockerfile "! -name '*.jar.original'" && pass 'container.artifact-selection' || fail 'container.artifact-selection'

  OPROD=services/smartfarm-order-service/src/main/resources/application-prod.yml
  GPROD=apps/smartfarm-gateway/src/main/resources/application-prod.yml
  contains "$OPROD" 'ddl-auto: validate' && contains "$OPROD" 'clean-disabled: true' && pass 'order.prod.flyway-jpa' || fail 'order.prod.flyway-jpa'
  contains "$OPROD" 'secret: ${ORDER_SERVICE_SECRET}' && not_contains "$OPROD" 'ORDER_SERVICE_SECRET:' && pass 'order.prod.required-secret' || fail 'order.prod.required-secret'
  contains "$GPROD" 'secret: ${GATEWAY_SERVICE_CLIENT_SECRET}' && pass 'gateway.prod.required-secret' || fail 'gateway.prod.required-secret'
  contains "$GPROD" 'api-docs:' && contains "$GPROD" 'swagger-ui:' && contains "$GPROD" 'enabled: false' && pass 'gateway.prod.docs-disabled' || fail 'gateway.prod.docs-disabled'
  contains "$OPROD" 'allow-local-http: false' && contains "$GPROD" 'allow-local-http: false' && pass 'prod.local-http-disabled' || fail 'prod.local-http-disabled'

  OAPP=services/smartfarm-order-service/src/main/resources/application.yml
  GAPP=apps/smartfarm-gateway/src/main/resources/application.yml
  contains "$OAPP" 'shutdown: graceful' && contains "$OAPP" 'await-termination: true' && pass 'order.graceful-shutdown' || fail 'order.graceful-shutdown'
  contains "$GAPP" 'shutdown: graceful' && pass 'gateway.graceful-shutdown' || fail 'gateway.graceful-shutdown'
  contains "$OAPP" 'include: readinessState,db,ping' && pass 'order.readiness-contract' || fail 'order.readiness-contract'
  contains "$GAPP" 'include: readinessState,ping' && pass 'gateway.readiness-contract' || fail 'gateway.readiness-contract'

  python3 -m json.tool observability/grafana/dashboards/smartfarm-order-operations.json >/dev/null 2>&1 && pass 'grafana.dashboard-json' || fail 'grafana.dashboard-json'
  if command -v promtool >/dev/null 2>&1; then
    promtool check rules observability/prometheus/order-alerts.yml >/dev/null 2>&1 && pass 'prometheus.rules' || fail 'prometheus.rules'
    promtool check config observability/prometheus/prometheus.yml >/dev/null 2>&1 && pass 'prometheus.config' || fail 'prometheus.config'
  else skip 'prometheus.validation' 'promtool unavailable'; fi

  K=deploy/kubernetes/order-gateway.yaml
  contains "$K" 'maxUnavailable: 0' && contains "$K" 'maxSurge: 1' && pass 'kubernetes.rolling-update' || fail 'kubernetes.rolling-update'
  contains "$K" 'readOnlyRootFilesystem: true' && contains "$K" 'allowPrivilegeEscalation: false' && pass 'kubernetes.security-context' || fail 'kubernetes.security-context'
  contains "$K" 'terminationGracePeriodSeconds: 45' && pass 'kubernetes.termination-grace' || fail 'kubernetes.termination-grace'
  contains "$K" 'startupProbe:' && contains "$K" 'readinessProbe:' && contains "$K" 'livenessProbe:' && pass 'kubernetes.probes' || fail 'kubernetes.probes'
  if command -v kubectl >/dev/null 2>&1; then
    kubectl apply --dry-run=client -f "$K" >/dev/null 2>&1 && pass 'kubernetes.client-dry-run' || fail 'kubernetes.client-dry-run'
  else skip 'kubernetes.client-dry-run' 'kubectl unavailable'; fi
  if grep -Eq 'REPLACE_WITH_DIGEST|OWNER/REPOSITORY' "$K"; then
    [ "$STRICT" = 1 ] && fail 'release.immutable-images' 'placeholder image remains' || skip 'release.immutable-images' 'replace placeholders before strict acceptance'
  elif grep -Eq 'image: .+@sha256:[0-9a-f]{64}' "$K"; then pass 'release.immutable-images' 'digest pinned'; else fail 'release.immutable-images' 'images are not digest pinned'; fi

  if grep -RIE '(password|secret|token)[[:space:]]*:[[:space:]]+[^$<{[:space:]][^[:space:]]+' deploy/kubernetes .env.production.example 2>/dev/null | grep -v 'Names only' >/dev/null; then
    fail 'release.no-committed-secrets' 'possible literal secret in deployment input'
  else pass 'release.no-committed-secrets'; fi
}

build_checks(){
  if ! command -v mvn >/dev/null 2>&1; then skip 'build.maven' 'mvn unavailable'; else
    mvn -B -pl :smartfarm-order-service,:smartfarm-gateway -am -DskipTests clean package >"$REPORT_DIR/maven-validation.log" 2>&1 \
      && pass 'build.maven' 'tests skipped by phase policy' || fail 'build.maven' 'see build/maven-validation.log'
  fi
  if ! command -v docker >/dev/null 2>&1; then skip 'build.docker' 'docker unavailable'; return; fi
  REV="$COMMIT"
  docker build --build-arg SERVICE_PATH=services/smartfarm-order-service --build-arg OCI_REVISION="$REV" -t smartfarm-order:validation . >"$REPORT_DIR/docker-order-validation.log" 2>&1 \
    && pass 'build.docker-order' || fail 'build.docker-order' 'see docker-order-validation.log'
  docker build --build-arg SERVICE_PATH=apps/smartfarm-gateway --build-arg OCI_REVISION="$REV" -t smartfarm-gateway:validation . >"$REPORT_DIR/docker-gateway-validation.log" 2>&1 \
    && pass 'build.docker-gateway' || fail 'build.docker-gateway' 'see docker-gateway-validation.log'
  for image in smartfarm-order:validation smartfarm-gateway:validation; do
    user="$(docker image inspect -f '{{.Config.User}}' "$image" 2>/dev/null)"
    health="$(docker image inspect -f '{{json .Config.Healthcheck}}' "$image" 2>/dev/null)"
    [ "$user" = '1001:1001' ] && pass "image.$image.non-root" "$user" || fail "image.$image.non-root" "$user"
    [ "$health" != 'null' ] && [ -n "$health" ] && pass "image.$image.healthcheck" || fail "image.$image.healthcheck"
  done
}

runtime_service(){
  name="$1"; base="$2"
  for endpoint in liveness readiness; do
    code="$(http_code "$base/actuator/health/$endpoint")"; status="$(json_status)"
    if [ "$code" = 200 ] && [ "$status" = UP ]; then pass "runtime.$name.$endpoint" 'HTTP 200 status UP'; else fail "runtime.$name.$endpoint" "HTTP $code status $status"; fi
  done
  code="$(http_code "$base/actuator/prometheus")"
  [ "$code" = 200 ] && pass "runtime.$name.prometheus" || fail "runtime.$name.prometheus" "HTTP $code"
}
runtime_checks(){
  runtime_service gateway "$GATEWAY_URL"
  runtime_service order "$ORDER_URL"
  http_code "$ORDER_URL/actuator/prometheus" >/dev/null
  for metric in smartfarm_order_saga_stale_claims smartfarm_order_outbox_stale_claims smartfarm_order_projection_gaps_stale_claims smartfarm_order_outbox_dead; do
    value="$(metric_value "$metric")"
    if [ -z "$value" ]; then fail "runtime.metric.$metric" 'metric missing'
    elif awk -v v="$value" 'BEGIN{exit !(v==0)}'; then pass "runtime.metric.$metric" '0'
    else fail "runtime.metric.$metric" "$value"; fi
  done
  for path in /graphiql /v3/api-docs /swagger-ui.html; do
    code="$(http_code "$GATEWAY_URL$path")"
    case "$code" in 401|403|404) pass "runtime.gateway.disabled.$path" "HTTP $code";; *) fail "runtime.gateway.disabled.$path" "HTTP $code";; esac
  done
}

case "$MODE" in
 static) static_checks;;
 build) static_checks; build_checks;;
 runtime) runtime_checks;;
 all|strict) static_checks; build_checks; runtime_checks;;
esac

cat >> "$REPORT" <<EOF

## Summary

- PASS: $PASS
- FAIL: $FAIL
- SKIP: $SKIP
- Acceptance: $([ "$FAIL" -eq 0 ] && echo PASS || echo FAIL)
EOF
rm -f "$REPORT_DIR/.validation-body"
echo "Report: $REPORT"
[ "$FAIL" -eq 0 ]
