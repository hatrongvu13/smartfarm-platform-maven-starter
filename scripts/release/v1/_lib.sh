#!/usr/bin/env bash
# Shared helpers for the SmartFarm v1 release test suite. Source this from each script:
#   source "$(dirname "$0")/_lib.sh"
#
# Env overrides: GW (gateway URL), TENANT, EMAIL, PASSWORD.

GW="${GW:-http://localhost:8080}"
TENANT="${TENANT:-farm-demo}"
EMAIL="${EMAIL:-admin@example.test}"
PASSWORD="${PASSWORD:-replace-with-a-long-random-password}"

PASS=0; FAIL=0
say(){ printf '   %s\n' "$*"; }
ok(){ PASS=$((PASS+1)); printf '   \033[32mPASS\033[0m %s\n' "$*"; }
no(){ FAIL=$((FAIL+1)); printf '   \033[31mFAIL\033[0m %s\n' "$*"; }
hdr(){ printf '\n== %s ==\n' "$*"; }
jget(){ printf '%s' "$1" | sed -n "s/.*\"$2\"[[:space:]]*:[[:space:]]*\"\{0,1\}\([^\",}]*\)\"\{0,1\}.*/\1/p" | head -1; }

# login -> exports ACCESS + AUTH array. Exits non-zero on failure.
login(){
  local r code body
  r=$(curl -sS -w '\n%{http_code}' -X POST "$GW/api/v1/auth/login" -H 'Content-Type: application/json' \
    -d "{\"tenantId\":\"$TENANT\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
  code=$(printf '%s' "$r" | tail -1); body=$(printf '%s' "$r" | sed '$d')
  if [ "$code" != "200" ]; then no "login (got $code)"; echo "$body"; return 1; fi
  ACCESS=$(jget "$body" accessToken); [ -n "$ACCESS" ] || ACCESS=$(jget "$body" access_token)
  [ -n "$ACCESS" ] || { no "login: no token in response"; return 1; }
  AUTH=(-H "Authorization: Bearer $ACCESS")
  ok "login (200) token=${ACCESS:0:20}..."
}

summary(){
  printf '\n---- %s: %d PASS, %d FAIL ----\n' "${1:-result}" "$PASS" "$FAIL"
  [ "$FAIL" = "0" ]
}
