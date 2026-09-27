#!/usr/bin/env bash
# 10 — Auth + RBAC (Phase 1/2): login, whoami, super-admin wildcard, role/scope listing.
set -uo pipefail
source "$(dirname "$0")/_lib.sh"

echo "SmartFarm v1 — Auth + RBAC"
login || exit 1

hdr "1. /auth/me trả roles + permissions"
R=$(curl -sS "$GW/api/v1/auth/me" "${AUTH[@]}")
printf '%s' "$R" | grep -q '"userId"' && ok "me trả userId" || no "me thiếu userId"
printf '%s' "$R" | grep -q 'inventory:write' && ok "super-admin có inventory:write (expand từ *)" || no "thiếu inventory:write"

hdr "2. Admin liệt kê role (phân quyền)"
R=$(curl -sS -w '\n%{http_code}' "$GW/api/v1/admin/roles" "${AUTH[@]}")
CODE=$(printf '%s' "$R" | tail -1); BODY=$(printf '%s' "$R" | sed '$d')
[ "$CODE" = "200" ] && ok "GET /admin/roles (200)" || no "admin/roles (got $CODE)"
printf '%s' "$BODY" | grep -q 'SUPERADMIN' && ok "có role SUPERADMIN" || no "thiếu SUPERADMIN"
printf '%s' "$BODY" | grep -q 'FARM_OPERATOR' && ok "có role FARM_OPERATOR" || no "thiếu FARM_OPERATOR"

hdr "3. Admin liệt kê permission (vốn từ scope)"
R=$(curl -sS -w '\n%{http_code}' "$GW/api/v1/admin/permissions" "${AUTH[@]}")
CODE=$(printf '%s' "$R" | tail -1); BODY=$(printf '%s' "$R" | sed '$d')
[ "$CODE" = "200" ] && ok "GET /admin/permissions (200)" || no "admin/permissions (got $CODE)"
for scope in orders:write inventory:write tasks:write report:write; do
  printf '%s' "$BODY" | grep -q "$scope" && ok "vốn từ có $scope" || say "chưa thấy $scope (có thể chưa seed)"
done

hdr "4. Token sai -> 401"
CODE=$(curl -sS -o /dev/null -w '%{http_code}' "$GW/api/v1/auth/me" -H "Authorization: Bearer invalid.token.here")
[ "$CODE" = "401" ] && ok "token rác -> 401" || no "token rác (got $CODE, muốn 401)"

summary "auth-rbac"
