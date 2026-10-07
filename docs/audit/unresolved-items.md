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

---

## Fix pass 2026-10-07 (part 2) — EVT-01/02 + MQTT HMAC wiring

**Resolved this pass (verified: build + 62 identity tests green, MAVEN_HOME confirmed):**

| ID | Resolution |
|----|-----------|
| EVT-01 | **RESOLVED (code).** Identity now emits protobuf `DomainEvent` instead of JSON. Added `IdentityLifecycleEvent` message to `libs/smartfarm-proto/.../events/v1/events.proto` (oneof field 24, generic `event_type`/`aggregate_type`/`actor_id` + `map<string,string> data`, mirroring the accepted `OrderLifecycleEvent` pattern). Rewrote `IdentityIntegrationEventPublisher` to build the proto; deleted the now-unused JSON `IdentityEventPayload`. The gateway WS bridge (`DomainEvent.parseFrom`) now accepts identity events. **Not yet verified live over MQTT** (needs services running). |
| EVT-02 | **RESOLVED (code).** `IdentityEventTopics.domain(...)` now emits the standard `smartfarm/<tenant>/_global/domain/<event>/v1` (dropped the non-standard `<aggregate>-<event>` segment); matches the WS filter `smartfarm/+/+/domain/#`. `farm` segment is the literal `_global` since identity is tenant-global. |
| ISSUE-02 | **Code wired (part 1, earlier this session):** HMAC sign/verify is on every producer+consumer; dev `sign+verify=ON` (shared dev secret), prod default-OFF with 2-phase rollout env knobs; simulator observer made envelope-aware; compose documents the shared dev secret + broker hardening pointer. **Broker ACL+TLS remains documented sample config, not enabled** (per direction). Still **not verified live over MQTT**. |

**Still OPEN after this pass:** ISSUE-02 live-verify (needs broker + services up), ISSUE-06, ISSUE-10/11/13/15/16, EVT-03 (health prod event path), GW-01/02.

---

## Live verification 2026-10-07 (part 3) — ISSUE-02 + EVT-01/02 PROVEN over MQTT

Verified end-to-end against running services (gateway + identity + order + livestock + finance +
inventory + reporting) on the dev Mosquitto broker (`127.0.0.1:1883`), MAVEN_HOME
`/Users/jaxmac/sdk/apache-maven-3.9.16`.

**Trigger (guaranteed real event):** wrong-password login against the real account
`root@system.test` (tenant `farm-demo`) → `AuthService.publishLoginFailure` → `sf_identity_outbox`
row → `IdentityOutboxRelay` (2s poll) → `IdentityMqttConnectionManager.sign(topic, payload)` → broker.

**Observed frame** (independent observer `BusVerifyProbe`, re-computes HMAC + parses protobuf):

```
topic   : smartfarm/farm-demo/_global/domain/failed/v1
bytes   : 448  prefix=53464d31...  magic=SFM1(signed)
verify  : ACCEPTED
proto   : DomainEvent OK  case=IDENTITY_LIFECYCLE_EVENT
identity: type=identity.login.failed aggType=user-security
          data={attemptCount=1, authenticationStage=PASSWORD, locked=false, subjectId=56b31cc7-...}
```

| ID | Live result |
|----|-------------|
| ISSUE-02 (app-level HMAC) | **VERIFIED LIVE.** Producer emitted an `SFM1` HMAC-SHA256 envelope (`53464d31` = `SFM1`); observer re-computed the topic-bound HMAC with the shared dev secret → ACCEPTED. Negative controls (`HmacEnforcementCheck`) all PASS: unsigned→REJECTED, tampered→REJECTED, replay-on-other-topic→REJECTED, wrong-secret→REJECTED. Enforcement proven in both directions, not just unwrap. |
| EVT-01 (identity protobuf) | **VERIFIED LIVE.** Inner payload parsed as protobuf `DomainEvent` with `case=IDENTITY_LIFECYCLE_EVENT` (new oneof field 24). Pre-fix this was JSON and would fail `parseFrom`. |
| EVT-02 (topic schema) | **VERIFIED LIVE.** Emitted topic `smartfarm/farm-demo/_global/domain/failed/v1` matches the WS-bridge filter `smartfarm/+/+/domain/#`. |

**Scope note:** broker ACL + mTLS remain documented sample config only (per direction), NOT enabled —
dev broker is `allow_anonymous true`. App-level HMAC is the enforced mechanism and is now live-proven.

**Verification scaffolding** (NOT product code) added under
`platform/smartfarm-farm-simulator/src/main/java/com/htv/smartfarm/simulator/verify/`:
`BusVerifyProbe` (live bus observer), `HmacEnforcementCheck` (negative controls), `IdentityDbProbe`
(read-only account/outbox inspector). Keep as reusable live-check tools or remove before commit.

---

## Fix pass 2026-10-07 (part 4) — GW-01 + EVT-03 (verified: build + health 5/5 tests green)

MAVEN_HOME `/Users/jaxmac/sdk/apache-maven-3.9.16`. Build+test level; live-verify needs a health +
gateway restart (deferred — host memory tight, services currently running the old jars).

| ID | Resolution |
|----|-----------|
| GW-01 | **RESOLVED (code).** Health-service is no longer orphaned from the gateway. Added `smartfarm.health.grpc-host/grpc-port` (`:9097`) to gateway `application.yml`; new `HealthDevClientConfig` (`@Bean healthChannel` + `AnimalHealthServiceBlockingStub`) and `HealthDevController` (`@RestController @Profile("dev & !prod")`, `/api/v1/health/{observations,vaccinations,alerts}`, scope `health:read`, per-service token for the `smartfarm-health` audience). Mirrors `ReportingDevClientConfig`/`ReportingDevController`. Security: covered by the gateway's `anyExchange().authenticated()` + method `@PreAuthorize` — no security-config change. Dev REST facade only (per decision); prod exposure is a separate pass. |
| EVT-03 | **RESOLVED (code), Option A (prod event path).** Removed `@Profile("dev & !prod")` from `HealthOutboxRelay`, `HealthOutboxScheduling`, `PahoHealthEventPublisher` — they are now active in every profile, gated by `smartfarm.health.outbox.enabled`. Relaxed the publisher's loopback-only constructor guard to accept a configured broker (`smartfarm.health.mqtt.url`), rejecting only a blank URL so a misconfigured prod fails loudly instead of silently no-op'ing. Added the previously-absent `smartfarm.health.outbox.{enabled,poll-ms}` + `smartfarm.health.mqtt.url` keys to base `application.yml` (enabled default `true`, loopback url default). **Note:** these keys never existed before, so the relay/publisher beans were inactive in *every* profile (not just prod) — health events were fully dead. Publisher still signs via `MqttSecurityVerifier` (HMAC), consistent with ISSUE-02. |

**Pre-existing bug found + fixed (in scope, same file):** gateway `application.yml` had `finance.grpc-port=9097`,
but finance-service actually serves gRPC on `9094` (`spring.grpc.server.port: 9094`); `9097` is in fact
health's port. Corrected finance to `9094`. This was a latent gateway→finance misroute, surfaced while
wiring health.

**Still OPEN:** ISSUE-02 live-verify DONE (part 3); EVT-01/02 DONE (part 3); GW-01/EVT-03 live-verify
(needs health + gateway restart); ISSUE-06, ISSUE-10/11/13/15/16, GW-02 (finance prod path).

---

## Fix pass 2026-10-07 (part 5) — ISSUE-06 reclassified (by-design)

| ID | Resolution |
|----|-----------|
| ISSUE-06 | **BY-DESIGN (not a bug), no code change.** `identity.command.result` has no *backend* consumer, but it is NOT orphaned: `IdentityMqttCommandDispatcher` (inbound MQTT admin commands, feature-flagged `smartfarm.identity.mqtt.commands.enabled`, default OFF) emits it on `smartfarm/<tenant>/_global/domain/command.result/v1`, which matches the gateway WS-bridge filter `smartfarm/+/+/domain/#`. After EVT-01 it reaches **WS clients** as a protobuf `IdentityLifecycleEvent` — it is the async **command-ack** for the FE that issued the command (matched via `commandId`/`correlationId`). This is a valid async-command → result-event → WS-push pattern. Decision (user): **keep the publisher**, document the contract (`docs/architecture/service-communications.md` → "Inbound MQTT commands + command-ack"), reclassify as by-design. Nothing is lost or broken; the whole command path is simply off until an operator enables the flag. |

**Still OPEN:** ISSUE-10/11/13/15/16, GW-02 (finance prod path), GW-01/EVT-03 live-verify.
