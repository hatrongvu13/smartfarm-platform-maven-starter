#!/usr/bin/env bash
set -euo pipefail

required_vars=(
  IDENTITY_IT_POSTGRES_URL
  IDENTITY_IT_POSTGRES_USER
  IDENTITY_IT_POSTGRES_PASSWORD
)

for name in "${required_vars[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    printf 'Required environment variable is missing: %s\n' "$name" >&2
    exit 2
  fi
done

if [[ "$IDENTITY_IT_POSTGRES_URL" != jdbc:postgresql://* ]]; then
  printf 'IDENTITY_IT_POSTGRES_URL must be a JDBC PostgreSQL URL.\n' >&2
  exit 2
fi

if [[ "$IDENTITY_IT_POSTGRES_URL" == *'user='* || "$IDENTITY_IT_POSTGRES_URL" == *'password='* ]]; then
  printf 'Do not put credentials in IDENTITY_IT_POSTGRES_URL; use the separate user/password variables.\n' >&2
  exit 2
fi

if [[ ! -f pom.xml || ! -f services/identity-service/pom.xml ]]; then
  printf 'Run this script from the repository root.\n' >&2
  exit 2
fi

maven=mvn
if [[ -x ./mvnw ]]; then
  maven=./mvnw
fi

printf 'Running Identity tests against the existing PostgreSQL server (schema: it_identity).\n'
printf 'This must target a disposable, non-production database; the test schema is retained for inspection.\n'
"$maven" -pl services/identity-service -am verify \
  -Dit.postgres.enabled=true \
  "-Dit.postgres.url=$IDENTITY_IT_POSTGRES_URL" \
  "-Dit.postgres.user=$IDENTITY_IT_POSTGRES_USER" \
  -Dspring.profiles.active=test
