# Security Architecture — SmartFarm Platform

> Reconciled 2026-10-15 from `libs/smartfarm-security`, identity service, gateway config, and current profile files.
> Phân loại: DESIGNED / IMPLEMENTED / CONFIGURED / TESTED.

## 1. Authentication
| Mechanism | Status | Chi tiết |
|-----------|--------|----------|
| Password login | IMPLEMENTED+CONFIGURED | BCrypt, lockout sau N lần fail, timing-defense chống user-enumeration |
| JWT access token | IMPLEMENTED | RS256, hard cap 15 phút, JWKS endpoint (`/.well-known/jwks.json`) |
| Refresh token | IMPLEMENTED | Rotating, family-based, reuse-detection; session/token hashes persisted with JPA in the identity database |
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
| Service token issuance | IMPLEMENTED, transport risk open | HTTP requires a dedicated shared internal header plus client credentials; gRPC issue/exchange require caller JWT plus client credentials; duplicate transports and TLS/mTLS remain open |
| gRPC TLS | DESIGNED | **off by default** — plaintext nội bộ |
| MQTT auth/ACL/mTLS | DESIGNED only | broker `allow_anonymous true`; ACL/mTLS = sample config (`docs/security/mqtt-acl-mtls.md`) |
| MQTT message HMAC envelope | IMPLEMENTED; profile-configured | HMAC code exists; dev profiles enable signing/verification and prod profiles default off. Verify each deployed publisher/consumer and rollout state independently. |

## 4. Secrets
- Dev: identity local PostgreSQL defaults include `postgres/root`; development HMAC and MFA keys are profile defaults and must not be reused in production.
- Prod: env (`SMARTFARM_JWT_ISSUER`, `GATEWAY_SERVICE_CLIENT_SECRET`, `IDENTITY_DB_*`...). `.env.production.example` làm template.
- Bootstrap no longer generates or logs a TOTP enrollment URI. MFA enrollment uses the separate challenge-token flow.
- Development-only secret/key defaults and local signing-key material exist in source/configuration; they are not production credentials and must never be reused in production. Production secrets must be supplied via an external secret-management mechanism.

## 5. Logging & audit
- Correlation ID xuyên gateway→service (`x-correlation-id`).
- gRPC audit (`audit-enabled`, default off ở gateway).

## 6. Known security gaps
- gRPC TLS off by default; private/loopback binding is not a substitute for authenticated transport.
- HTTP `POST /internal/service-token` requires `IDENTITY_INTERNAL_API_SECRET` in a dedicated header but still passes the bearer-token filter chain; gRPC issue/exchange require a caller JWT. Consolidate the duplicate transports and enable TLS/mTLS; a shared header is not encryption.
- No dedicated server-level test currently proves registration and enforcement of the five identity token/session RPC handlers.
- MQTT broker chưa auth/TLS/ACL (ISSUE-02 + RISK-BROKER-01).
- App-level HMAC signing/verification is configured on in development and off by default in production; production broker authentication/TLS/ACL is not enabled by these settings.
- Super-admin SCOPE_* bypass chưa audit-log (ISSUE-12).

## 7. Security checklist
- [x] JWT RS256 + JWKS + audience/tenant
- [x] Rotating refresh + reuse detection
- [x] TOTP MFA + lockout
- [x] DB RBAC + gRPC fail-closed authz
- [x] Correlation propagation
- [-] Protect service-token issuance with caller authentication and a shared internal header; TLS/mTLS and duplicate-path removal remain
- [x] Stop bootstrap from generating/logging TOTP enrollment secret
- [ ] gRPC TLS enabled
- [ ] MQTT broker auth + TLS + ACL
- [x] MQTT message HMAC configured on in dev; production defaults off and rollout remains operator-controlled
- [ ] Super-admin bypass audit-logged
- [ ] Dependency/container scanning (CI có SBOM/provenance; chưa thấy scan gate)

← [Documentation Index](../index.md) · [threat-model.md](threat-model.md) · [Identity backlog](../../IDENTITY_BACKLOG_UPDATED.md)

## 8. Delegated actor contract

For Gateway-to-service calls, the service token authenticates the machine caller while
`RequestContext.actor_id` carries the original authenticated end-user:

- service token `sub`: `svc:<clientId>`
- service token `client_id`: the registered machine client
- `RequestContext.actor_id`: the end-user subject verified by Gateway
- delegation gate: `SCOPE_identity:principal:impersonate`

Identity accepts an actor different from the service-token subject only for a SERVICE token with
that explicit authority. The verified actor is then used for self-service targeting and audit/event
metadata. User tokens cannot delegate actors. `context.correlation_id` must still match the
`x-correlation-id` gRPC metadata value.
