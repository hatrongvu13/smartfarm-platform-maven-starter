#!/usr/bin/env bash
set -euo pipefail

required_vars=(
  IDENTITY_DB_URL
  IDENTITY_DB_USER
  IDENTITY_DB_PASSWORD
  IDENTITY_ISSUER
  IDENTITY_JWK_SET_URI
  IDENTITY_KEY_ID
  IDENTITY_PRIVATE_KEY_PATH
  IDENTITY_PUBLIC_KEY_PATH
  IDENTITY_MFA_ENCRYPTION_KEY
  IDENTITY_MQTT_URL
)

for name in "${required_vars[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    printf 'Required environment variable is missing: %s\n' "$name" >&2
    exit 2
  fi
done

for command in mvn java curl python3; do
  if ! command -v "$command" >/dev/null 2>&1; then
    printf 'Required command is not installed: %s\n' "$command" >&2
    exit 2
  fi
done

if [[ ! -f pom.xml || ! -f services/identity-service/pom.xml ]]; then
  printf 'Run this script from the repository root.\n' >&2
  exit 2
fi

maven=mvn
if [[ -x ./mvnw ]]; then
  maven=./mvnw
fi

http_port="${IDENTITY_GATE_HTTP_PORT:-18092}"
grpc_port="${IDENTITY_GATE_GRPC_PORT:-19092}"
wait_seconds="${IDENTITY_GATE_WAIT_SECONDS:-90}"
mqtt_client_id="${IDENTITY_GATE_MQTT_CLIENT_ID:-farmverse-identity-gate-$$}"
if [[ ! "$http_port" =~ ^[0-9]+$ || ! "$grpc_port" =~ ^[0-9]+$ \
  || ! "$wait_seconds" =~ ^[1-9][0-9]*$ ]]; then
  printf 'Identity gate ports and wait duration must be positive integers.\n' >&2
  exit 2
fi

log_file="$(mktemp "${TMPDIR:-/tmp}/identity-runtime-gate.XXXXXX")"
identity_pid=""
cleanup() {
  local status=$?
  trap - EXIT INT TERM
  if [[ -n "$identity_pid" ]] && kill -0 "$identity_pid" 2>/dev/null; then
    kill "$identity_pid" 2>/dev/null || true
    wait "$identity_pid" 2>/dev/null || true
    printf 'Stopped temporary Identity process (pid %s); PostgreSQL and MQTT were not started or stopped.\n' \
      "$identity_pid"
  fi
  if [[ $status -ne 0 && -f "$log_file" ]]; then
    printf '\nIdentity startup log:\n' >&2
    cat "$log_file" >&2
  fi
  rm -f "$log_file"
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

printf 'Building Identity without starting PostgreSQL or MQTT.\n'
"$maven" -pl services/identity-service -am -DskipTests package

artifact="services/identity-service/target/identity-service-0.1.0-SNAPSHOT.jar"
if [[ ! -f "$artifact" ]]; then
  printf 'Expected runnable artifact was not produced: %s\n' "$artifact" >&2
  exit 1
fi

printf 'Starting Identity temporarily; it will be stopped automatically after the readiness gate.\n'
java -jar "$artifact" \
  --spring.profiles.active=prod \
  --server.address=127.0.0.1 \
  --server.port="$http_port" \
  --spring.grpc.server.address=127.0.0.1 \
  --spring.grpc.server.port="$grpc_port" \
  --farmverse.identity.mqtt.enabled=true \
  --farmverse.identity.mqtt.client-id="$mqtt_client_id" \
  --farmverse.identity.mqtt.clean-session=true \
  --farmverse.identity.mqtt.commands.enabled=false \
  --farmverse.identity.outbox.enabled=false \
  --management.endpoint.health.show-components=always \
  --management.endpoint.health.show-details=always \
  >"$log_file" 2>&1 &
identity_pid=$!

health_url="http://127.0.0.1:$http_port/actuator/health"
for ((attempt = 1; attempt <= wait_seconds; attempt++)); do
  if ! kill -0 "$identity_pid" 2>/dev/null; then
    printf 'Identity exited before becoming ready.\n' >&2
    exit 1
  fi
  health="$(curl --silent --show-error --fail --max-time 2 "$health_url" 2>/dev/null || true)"
  if [[ -n "$health" ]] && printf '%s' "$health" | python3 -c '
import json, sys
try:
    health = json.load(sys.stdin)
    components = health.get("components", {})
    ready = (
        health.get("status") == "UP"
        and components.get("db", {}).get("status") == "UP"
        and components.get("identityMqtt", {}).get("status") == "UP"
    )
    sys.exit(0 if ready else 1)
except (ValueError, AttributeError):
    sys.exit(1)
'; then
    printf 'PASS: Identity, PostgreSQL, and MQTT are UP. gRPC is bound to %s:%s.\n' \
      "127.0.0.1" "$grpc_port"
    exit 0
  fi
  sleep 1
done

printf 'Identity did not report UP with PostgreSQL and MQTT within %s seconds.\n' \
  "$wait_seconds" >&2
exit 1
