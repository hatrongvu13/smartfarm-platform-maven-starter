#!/usr/bin/env bash
# graphql/test-introspection.sh — GraphQL introspection probe.
#
# Introspection is a standard schema-discovery query. The gateway may DISABLE it
# in production for security (spring.graphql.schema.introspection.enabled=false).
# Honors GRAPHQL_INTROSPECTION_ENABLED:
#   true  -> introspection MUST succeed (data.__schema present)
#   false -> introspection being blocked is EXPECTED (not a failure)
set -o errexit -o nounset -o pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"
source "$SCRIPT_DIR/../lib/http.sh"
source "$SCRIPT_DIR/../lib/assertions.sh"
source "$SCRIPT_DIR/../lib/auth.sh"
load_env "${1:-}"

# Introspection may require auth depending on security config — attach a token if we can.
require_login || true

Q='{"query":"{ __schema { queryType { name } mutationType { name } subscriptionType { name } } }"}'

log_section "graphql — introspection (GRAPHQL_INTROSPECTION_ENABLED=${GRAPHQL_INTROSPECTION_ENABLED})"
graphql_request "$Q"

if [ "${GRAPHQL_INTROSPECTION_ENABLED}" = "true" ]; then
  if [ "$HTTP_STATUS" = "200" ] && printf '%s' "$HTTP_BODY" | jq -e '.data.__schema.queryType.name != null' >/dev/null 2>&1; then
    pass "introspection returned schema (queryType=$(printf '%s' "$HTTP_BODY" | jq -r '.data.__schema.queryType.name'))"
  else
    fail "introspection expected to succeed but did not (HTTP $HTTP_STATUS)"
    _dump_body
  fi
else
  # Blocked is expected: a disabled-introspection server returns errors or no __schema.
  if printf '%s' "$HTTP_BODY" | jq -e '.data.__schema != null' >/dev/null 2>&1; then
    fail "introspection is ENABLED but config says it should be disabled"
  else
    pass "introspection disabled as expected (GRAPHQL_INTROSPECTION_ENABLED=false)"
  fi
fi

summary "graphql-introspection"
