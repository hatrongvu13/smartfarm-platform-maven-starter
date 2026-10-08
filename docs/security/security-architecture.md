# Security Architecture — SmartFarm Platform

> Verified from `libs/smartfarm-security`, identity service, gateway config @ HEAD `eaaa112`.
> Phân loại: DESIGNED / IMPLEMENTED / CONFIGURED / TESTED.

## 1. Authentication
| Mechanism | Status | Chi tiết |
|-----------|--------|----------|
| Password login | IMPLEMENTED+CONFIGURED | BCrypt, lockout sau N lần fail, timing-defense chống user-enumeration |
| JWT access token | IMPLEMENTED | RS256, hard cap 15 phút, JWKS endpoint (`/.well-known/jwks.json`) |
| Refresh token | IMPLEMENTED | Rotating, family-based, reuse-detection → revoke family (Redis) |
| MFA (TOTP) | IMPLEMENTED | Secret mã hóa, recovery/challenge token hashed, challenge lockout |

## 2. Authorization
| | Status | Chi tiết |
|-|--------|----------|
| RBAC | IMPLEMENTED+CONFIGURED | DB role→permission, per-farm scope, tenant membership |
| gRPC method authz | IMPLEMENTED | `GrpcMethodPolicy` exact-method, fail-closed, tenant pinning |
| REST scope | IMPLEMENTED | `@PreAuthorize("hasAuthority('SCOPE_...')")` trên order/saga |
| Super-admin | IMPLEMENTED (⚠️) | `isSuperAdmin()` bypass qua `SCOPE_*` — ISSUE-12, nên audit-log |

## 3. Transport & inter-service
| | Status | Gap |
|-|--------|-----|
| JWT validation (gateway) | IMPLEMENTED | issuer + audience + tenant + JWKS |
| Service token (gateway→service) | IMPLEMENTED | per-audience token |
| gRPC TLS | DESIGNED | **off by default** — plaintext nội bộ |
| MQTT auth/ACL/mTLS | DESIGNED only | broker `allow_anonymous true`; ACL/mTLS = sample config (`docs/security/mqtt-acl-mtls.md`) |
| MQTT message HMAC envelope | IMPLEMENTED + LIVE-VERIFIED | Signed envelope is wired to producers/consumers; production rollout remains operator-controlled |

## 4. Secrets
- Dev: default password trong `application-dev.yml` (postgres/root) — ISSUE-16, dev-only.
- Prod: env (`SMARTFARM_JWT_ISSUER`, `GATEWAY_SERVICE_CLIENT_SECRET`, `IDENTITY_DB_*`...). `.env.production.example` làm template.
- **Không có secret thật trong repo/tài liệu.**

## 5. Logging & audit
- Correlation ID xuyên gateway→service (`x-correlation-id`).
- gRPC audit (`audit-enabled`, default off ở gateway).

## 6. Known security gaps (→ `docs/audit/unresolved-items.md`)
- gRPC TLS off (DESIGNED).
- MQTT broker chưa auth/TLS/ACL (ISSUE-02 + RISK-BROKER-01).
- Production broker authentication/TLS/ACL is not enabled; app-level HMAC is implemented and live-verified.
- 3 placeholder class rỗng (xác định trong scan security).
- Super-admin SCOPE_* bypass chưa audit-log (ISSUE-12).

## 7. Security checklist
- [x] JWT RS256 + JWKS + audience/tenant
- [x] Rotating refresh + reuse detection
- [x] TOTP MFA + lockout
- [x] DB RBAC + gRPC fail-closed authz
- [x] Correlation propagation
- [ ] gRPC TLS enabled
- [ ] MQTT broker auth + TLS + ACL
- [x] MQTT message HMAC wired and live-verified; production flags remain rollout-controlled
- [ ] Super-admin bypass audit-logged
- [ ] Dependency/container scanning (CI có SBOM/provenance; chưa thấy scan gate)

← [Documentation Index](../index.md) · [threat-model.md](threat-model.md)
