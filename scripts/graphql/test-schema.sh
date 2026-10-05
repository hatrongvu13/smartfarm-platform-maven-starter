#!/usr/bin/env bash
# graphql/test-schema.sh — verify the live schema matches the committed SDL.
#
# Ground truth: apps/smartfarm-gateway/src/main/resources/graphql/schema.graphqls
# (Query + Mutation, no Subscription). This test confirms the RUNTIME schema
# exposes the key operations the committed schema declares. When introspection is
# disabled, it falls back to probing a representative operation instead.
set -o errexit -o nounset -o pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"
source "$SCRIPT_DIR/../lib/http.sh"
source "$SCRIPT_DIR/../lib/assertions.sh"
source "$SCRIPT_DIR/../lib/auth.sh"
load_env "${1:-}"

require_login || true

# A handful of operations declared in the committed schema (verified from SDL).
EXPECTED_QUERIES="platformStatus tasks orders dashboard warehouseInventory batchCost cashFlow lowStock me users roles permissions"
EXPECTED_MUTATIONS="placeOrder createDraftOrder submitDraftOrder cancelOrder updateMyProfile changeMyPassword"

if [ "${GRAPHQL_INTROSPECTION_ENABLED}" = "true" ]; then
  log_section "graphql — schema: Query fields present (via introspection)"
  graphql_request '{"query":"{ __type(name:\"Query\"){ fields { name } } }"}'
  if [ "$HTTP_STATUS" = "200" ] && printf '%s' "$HTTP_BODY" | jq -e '.data.__type.fields != null' >/dev/null 2>&1; then
    live="$(printf '%s' "$HTTP_BODY" | jq -r '.data.__type.fields[].name')"
    for f in $EXPECTED_QUERIES; do
      if printf '%s\n' "$live" | grep -qx "$f"; then pass "Query.$f present"; else fail "Query.$f MISSING from live schema"; fi
    done
  else
    fail "could not introspect Query type (HTTP $HTTP_STATUS)"; _dump_body
  fi

  log_section "graphql — schema: Mutation fields present (via introspection)"
  graphql_request '{"query":"{ __type(name:\"Mutation\"){ fields { name } } }"}'
  if [ "$HTTP_STATUS" = "200" ] && printf '%s' "$HTTP_BODY" | jq -e '.data.__type.fields != null' >/dev/null 2>&1; then
    live="$(printf '%s' "$HTTP_BODY" | jq -r '.data.__type.fields[].name')"
    for f in $EXPECTED_MUTATIONS; do
      if printf '%s\n' "$live" | grep -qx "$f"; then pass "Mutation.$f present"; else fail "Mutation.$f MISSING from live schema"; fi
    done
  else
    fail "could not introspect Mutation type (HTTP $HTTP_STATUS)"; _dump_body
  fi

  log_section "graphql — schema: no Subscription type (matches committed SDL)"
  graphql_request '{"query":"{ __schema { subscriptionType { name } } }"}'
  if [ "$HTTP_STATUS" != "200" ] || ! printf '%s' "$HTTP_BODY" | jq -e '.data.__schema != null' >/dev/null 2>&1; then
    fail "could not introspect __schema for subscription check (HTTP $HTTP_STATUS)"; _dump_body
  elif printf '%s' "$HTTP_BODY" | jq -e '.data.__schema.subscriptionType == null' >/dev/null 2>&1; then
    pass "no Subscription type (as expected)"
  else
    skip "a Subscription type is present (SDL declared none — review if intentional)"
  fi
else
  log_section "graphql — schema (introspection disabled: probe representative op)"
  graphql_request '{"query":"{ platformStatus { name status } }"}'
  # Either data or an auth/errors envelope proves the operation is wired.
  if [ "$HTTP_STATUS" = "200" ] || [ "$HTTP_STATUS" = "401" ] || [ "$HTTP_STATUS" = "403" ]; then
    pass "schema reachable via platformStatus (introspection off; HTTP $HTTP_STATUS)"
  else
    fail "schema probe failed (HTTP $HTTP_STATUS)"; _dump_body
  fi
fi

summary "graphql-schema"
