#!/usr/bin/env bash
# graphql/gateway/test-graphql.sh — exercise the gateway GraphQL operations.
#
# Loads .graphql operation files from queries/ and mutations/ (one op per file),
# posts them to GRAPHQL_URL, and asserts the full GraphQL envelope (HTTP 200,
# valid JSON, .data present, no .errors) — NOT just HTTP status, because the
# gateway returns HTTP 200 with an "errors" array for a failed operation.
#
# Queries are read-only and always run (when authenticated). Mutations WRITE and
# run only with ALLOW_DESTRUCTIVE_TESTS=true, scoped to a unique idempotencyKey
# and cleaned up afterwards.
set -o errexit -o nounset -o pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../../lib/common.sh"
source "$SCRIPT_DIR/../../lib/http.sh"
source "$SCRIPT_DIR/../../lib/assertions.sh"
source "$SCRIPT_DIR/../../lib/auth.sh"
load_env "${1:-}"

QDIR="$SCRIPT_DIR/queries"
MDIR="$SCRIPT_DIR/mutations"

# gql_file <name> <path-to-.graphql> <variables-json>
# Builds the {query, variables} payload with jq (so the query string is JSON-safe)
# and asserts the success envelope.
gql_file() {
  local name="$1" file="$2" vars="${3:-{\}}"
  [ -f "$file" ] || { skip "$name (missing op file: $file)"; return 0; }
  local q payload
  q="$(cat "$file")"
  payload="$(jq -nc --arg q "$q" --argjson v "$vars" '{query:$q, variables:$v}')"
  graphql_request "$payload"
  assert_graphql_ok "$name"
}

if ! require_login; then
  log_warn "no usable credentials — running only the unauthenticated platformStatus probe"
  log_section "graphql — platformStatus (unauthenticated)"
  gql_file "platformStatus" "$QDIR/platform-status.graphql" '{}'
  summary "graphql-gateway"; exit $?
fi

log_section "graphql — queries (read-only)"
gql_file "platformStatus" "$QDIR/platform-status.graphql" '{}'
gql_file "me"             "$QDIR/me.graphql"              '{}'
gql_file "tasks"          "$QDIR/tasks.graphql"          "$(jq -nc --arg f "$TEST_FARM_ID" '{farmId:$f, limit:5}')"
gql_file "orders"         "$QDIR/orders.graphql"         "$(jq -nc --arg f "$TEST_FARM_ID" '{farmId:$f, limit:5}')"
gql_file "dashboard"      "$QDIR/dashboard.graphql"      "$(jq -nc --arg f "$TEST_FARM_ID" '{farmId:$f, recentLimit:5}')"
gql_file "cashFlow"       "$QDIR/cash-flow.graphql"      "$(jq -nc --arg f "$TEST_FARM_ID" '{farmId:$f}')"
gql_file "lowStock"       "$QDIR/low-stock.graphql"      "$(jq -nc --arg f "$TEST_FARM_ID" '{farmId:$f, limit:20}')"

log_section "graphql — error-response contract (HTTP 200 + errors)"
# A query missing a required argument must return the errors envelope, NOT crash.
graphql_request '{"query":"query { tasks { taskId } }"}'
assert_graphql_has_errors "tasks without required farmId -> GraphQL errors"

log_section "graphql — RBAC: garbage bearer token is rejected"
_saved="${AUTH_TOKEN:-}"; AUTH_TOKEN="garbage.garbage.garbage"
graphql_request "$(jq -nc --arg f "$TEST_FARM_ID" '{query:"query($f:ID!){ tasks(farmId:$f){ taskId } }", variables:{f:$f}}')"
# Either transport-level 401/403, or 200 with errors — both are valid rejections.
if [ "$HTTP_STATUS" = "401" ] || [ "$HTTP_STATUS" = "403" ]; then
  pass "garbage token -> HTTP $HTTP_STATUS"
else
  assert_graphql_has_errors "garbage token -> GraphQL errors"
fi
AUTH_TOKEN="$_saved"

log_section "graphql — safe mutation lifecycle [destructive-gated]"
if [ "${ALLOW_DESTRUCTIVE_TESTS}" = "true" ]; then
  if [ -z "${TEST_ITEM_ID:-}" ] || [ -z "${TEST_WAREHOUSE_ID:-}" ]; then
    skip "mutation lifecycle needs TEST_ITEM_ID + TEST_WAREHOUSE_ID"
  else
    idem="test-gql-order-$(date +%s)-$$"
    input="$(jq -nc --arg k "$idem" --arg f "$TEST_FARM_ID" --arg i "$TEST_ITEM_ID" --arg w "$TEST_WAREHOUSE_ID" '{
      input:{ idempotencyKey:$k, farmId:$f,
        lines:[{itemId:$i, quantity:"1", unit:"EA", currency:"VND", unitPriceMinor:1000, warehouseId:$w}] } }')"
    gql_file "createDraftOrder" "$MDIR/create-draft-order.graphql" "$input"
    oid="$(printf '%s' "$HTTP_BODY" | jq -r '.data.createDraftOrder.orderId // empty')"
    ver="$(printf '%s' "$HTTP_BODY" | jq -r '.data.createDraftOrder.version // 0')"
    if [ -n "$oid" ]; then
      log_info "created draft order orderId=$oid (idempotencyKey=$idem) — cleaning up"
      del="$(jq -nc --arg o "$oid" --argjson v "${ver:-0}" '{input:{orderId:$o, expectedVersion:$v}}')"
      gql_file "deleteDraftOrder (cleanup)" "$MDIR/delete-draft-order.graphql" "$del"
    fi
  fi
else
  skip "mutation lifecycle (set ALLOW_DESTRUCTIVE_TESTS=true + TEST_ITEM_ID/TEST_WAREHOUSE_ID)"
fi

summary "graphql-gateway"
