# Unresolved Items — SmartFarm Platform

> Source: `docs/audit/04-issue-register.md` + scan 2026-10-06. Chỉ liệt kê issue thực sự còn OPEN có bằng chứng.

| ID | Service | Mô tả | Severity | Bằng chứng | Priority | Target Version | Điều kiện hoàn thành |
|----|---------|-------|----------|------------|----------|----------|---------------------|
| ISSUE-02 | messaging/identity/order/livestock/health | MQTT HMAC wiring incomplete: HMAC envelope impl + default-off; chưa nối verifier.sign/verify vào publisher/consumer; broker `allow_anonymous true` | CRITICAL | `libs/smartfarm-security/MqttSecurityEnvelope.java` implemented, `smartfarm.mqtt.security.sign-enabled=false` | P2 | V1 | `sign-enabled=true` → publisher sign → consumer verify → broker ACL+TLS |
| ISSUE-06 | identity | `identity.command.result` event published, **no consumer** (orphan producer) | MEDIUM | `IdentityIntegrationEventPublisher` publish, `grep -r command.result` zero consumer | P2 | V1 | Add consumer or remove publisher |
| ISSUE-07 | security lib | Trùng lặp AutoConfiguration gRPC security (2 class cùng tên khác package) | MEDIUM | Source | P3 | V1 | Hợp nhất 1 class |
| ISSUE-10 | order | `OrderMqttPublisher` `cleanSession=true` + random client-id → no persistent session publisher-side | MEDIUM | Source | P3 | V1 | Stable client-id + `cleanSession=false` (requires broker ACL) |
| ISSUE-11 | security lib | `JwtServerInterceptor` không check audience per-service (phụ thuộc `SmartFarmJwtValidator`) | MEDIUM | Source | P3 | V2 | Per-service audience enforcement in interceptor |
| ISSUE-12 | identity | `isSuperAdmin()` cấp bypass `SCOPE_*` authority — nên audit-log super-admin bypass | MEDIUM | Source | P2 | V1 | Audit log khi bypass; document as intentional or restrict |
| ISSUE-13 | order/inventory/livestock | Thiếu timeout/circuit-breaker consumer MQTT; gRPC deadline 5s cố định không cấu hình | MEDIUM | `GatewayGrpcChannelFactory` hardcode or config default 5s | P3 | V2 | Externalize deadline config; add circuit-breaker |
| ISSUE-15 | all services | `ddl-auto` không nhất quán: dev=`update`, prod=`validate` — risk: dev schema drift | LOW | `application-dev.yml` vs `application-prod.yml` | P4 | V2 | Standardize Flyway-only |
| ISSUE-16 | all services | Mật khẩu/secret mặc định trong `application-dev.yml` (postgres/root) | LOW | `application-dev.yml` passwords | P4 | V1 | Externalize via env; document clearly as dev-only |

## Phát hiện mới (scan 2026-10-06)

| ID | Service | Mô tả | Severity | Bằng chứng |
|----|---------|-------|----------|------------|
| EVT-01 | identity | Event payload = **JSON**, mọi service khác = protobuf `DomainEvent` → gateway WS bridge parse fail cho event identity | HIGH | `IdentityIntegrationEventPublisher` vs `OrderEventMapper`/gateway `MqttEventSubscriber` |
| EVT-02 | identity | MQTT topic schema khác chuẩn: `smartfarm/{tenant}/_global/domain/{aggr}-{event}/v{n}` (hyphen, no farm segment) vs `smartfarm/{tenant}/{farm}/domain/.../v1` | MEDIUM | Source topic builder |
| EVT-03 | health | Health event publisher = `@Profile("dev & !prod")` + reject non-loopback broker → **no prod event path** | HIGH | `PahoHealthEventPublisher`, `HealthOutboxRelay` |
| GW-01 | health | **Health-service hoàn toàn orphan** — không REST, không GraphQL, không gRPC client khai báo trong gateway | HIGH | Gateway `application.yml` thiếu `health:` section |
| GW-02 | finance | Finance chỉ có 2 dev-only GraphQL query; không REST, không prod path qua gateway | MEDIUM | `FarmGraphQlController` dev-only + no prod controller |
| DOC-01 | README | **6/8 internal link gãy**: `docs/v1/`, `docs/shared/`, `SMARTFARM-VERSION.md`, `docs/README.md`, `docs/backlog/README.md` | HIGH | `ls` verify |
| DOC-02 | README | Module count "13" — thực tế 14 trong reactor pom | LOW | `pom.xml` vs README |

← [Documentation Audit](documentation-audit.md) · [Resolved Issues](../history/resolved-issues.md)


---

## Fix pass 2026-10-07 (verified against source + live dev)

**Resolved / re-classified this pass:**

| ID | Resolution |
|----|-----------|
| ISSUE-07 | **FALSE POSITIVE — won't fix.** Verified: the security lib has 3 distinct `@AutoConfiguration` classes (properties, gRPC security, MQTT security), each a separate concern — no duplication. `IdentityGrpcSecurityConfiguration` is identity's own gRPC *policy* config, not a copy of the lib auto-config. Nothing to consolidate. |
| ISSUE-12 | Partially addressed: super-admin now authorizes via an explicit wildcard `*` scope (expanded in `JwtAuthorities.KNOWN_SCOPES`) instead of a silent bypass. Audit-logging of super-admin actions still open (P2). |
| DOC-01 | **RESOLVED** — README rewritten as a portal; all internal links fixed (0 broken). |
| DOC-02 | **RESOLVED** — README module count corrected. |
| CFG-01 / ISSUE-01 / ISSUE-19 | **RESOLVED** earlier (profile config + nested jwt.* + test profiles). |

**New capability added (not an issue — feature):**
- `GET /api/v1/platform/deployment-state` (public, pre-login, via gateway → identity): returns
  `{initialized, superAdminExists, bootstrapRequired}` so the FE knows a fresh deployment and routes
  to the one-time super-admin bootstrap. Verified live: flips correctly before/after bootstrap.

**REST ↔ gRPC boundary — audited, COMPLIANT (no migration needed):**
- Inter-service **business** communication is already gRPC (order↔inventory↔finance; gateway→services).
- REST is confined to the allowed tiers: gateway edge/public (`AuthProxyController`, `WhoAmIController`,
  `OrderRestController`, `OrderSagaAdminController`, `PlatformStateController`), OAuth2/auth
  (`AuthController`), actuator/health, dev facades (`@Profile("dev & !prod")`), and `/internal/*`
  ops-admin endpoints (loopback-bound in prod, scope-gated) for outbox/saga/projection recovery.
- The `/internal/*` admin endpoints are intentionally kept REST (human/script ops tooling), not an
  inter-service business path — classified `[KEEP]`, not a boundary violation.

**Still OPEN (unchanged, need their own focused pass):** ISSUE-02 (MQTT HMAC enable + broker ACL/TLS,
CRITICAL), ISSUE-06 (orphan `identity.command.result`), ISSUE-10/11/13/15/16, EVT-01/02/03, GW-01/02.
These are feature/security gaps, not quick fixes — see the status doc for the roadmap.