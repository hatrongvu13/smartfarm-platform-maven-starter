# Module Maturity Register

PHASE 2 module-maturity assessment. Verdicts are grounded in source read directly
(entry points, persistence, auth/tenant scope, error handling, config, tests), with
`graphslice.py` used to locate candidates and orphans. Each line is tagged
**FACT** (read in source / counted from the graph), **INFERENCE** (reasoned from
evidence), **UNKNOWN** (not determinable from static read), or **RISK**.

Scope note (FACT): the dependency graph `graphify-out/graph.json` was generated
2026-10-02 08:56, **before** `libs/smartfarm-messaging` existed, so that module has
0 nodes in the graph and is classified from source only. Prior remediation already
in the tree is NOT re-flagged as a defect: ISSUE-01/02 (MQTT HMAC envelope +
verifier), ISSUE-03 (dead `OrderSagaOrchestrator` removed), ISSUE-04/05/09
(`libs/smartfarm-messaging` shared classifier + MQTT factory, outbox poison handling,
durable `OrderChangedConsumer`, classifier applied to identity dispatcher).

Maturity scale: `PRODUCTION_IMPLEMENTATION` / `PARTIAL_IMPLEMENTATION` /
`DEMO_OR_SKELETON` / `STALE_OR_DEAD` / `UNKNOWN`.

## Part 1 — CHUNK 01-07 (libs, identity, order, gateway, inventory, livestock, health)

### Verdict summary

| Chunk | Module | Nodes | Verdict |
|---|---|---|---|
| 01 | libs/smartfarm-common-kernel | 87 | PRODUCTION_IMPLEMENTATION |
| 01 | libs/smartfarm-security | 196 | PRODUCTION_IMPLEMENTATION |
| 01 | libs/smartfarm-messaging | 0 (graph predates) | PRODUCTION_IMPLEMENTATION |
| 01 | libs/smartfarm-proto | 1 | PRODUCTION_IMPLEMENTATION (generated) |
| 02 | services/smartfarm-identity | 1144 | PRODUCTION_IMPLEMENTATION |
| 03 | services/smartfarm-order | 960 | PRODUCTION_IMPLEMENTATION |
| 04 | apps/smartfarm-gateway | 355 | PRODUCTION_IMPLEMENTATION |
| 05 | services/smartfarm-inventory | 168 | PARTIAL_IMPLEMENTATION |
| 06 | services/smartfarm-livestock | 216 | PRODUCTION_IMPLEMENTATION |
| 07 | services/smartfarm-health | 99 | PARTIAL_IMPLEMENTATION |

---

### CHUNK-01 — libs (common-kernel / security / messaging / proto)

**libs/smartfarm-common-kernel** — 87 nodes; inbound 143 / outbound 36 (FACT).
- Entry points: none (library). Annotations: 1 `@Entity` (FACT).
- Persistence: none of its own; provides base event/outbox/paging primitives
  (`DomainEvent`, `EventMetadata`, `OutboxStatus`, `PageQuery`, `PageResult`,
  `BusinessException`, `RequestMetadata`) (FACT, from test coverage of each).
- Tests: 7 unit tests, one per primitive (FACT).
- Demo/dead candidates: none found (FACT).
- **Verdict: PRODUCTION_IMPLEMENTATION** — heavily consumed shared kernel (143
  inbound cross-module edges), immutable primitives with test coverage (INFERENCE).

**libs/smartfarm-security** — 196 nodes; inbound 131 / outbound 333 (FACT).
- Entry points: none (library); 7 `@Configuration`, 5 `@Service`, plus Spring
  Boot auto-configuration under `META-INF` (`SmartFarmMqttSecurityAutoConfiguration`,
  `SmartFarmGrpcSecurityAutoConfiguration`, `SmartFarmSecurityPropertiesAutoConfiguration`)
  (FACT) — so "orphan" classes here are wired by auto-config, not dead.
- Real security: `RsaJwtIssuer`, `JwtTokenVerifier`, `SmartFarmJwtValidator`,
  `JwtServerInterceptor` (gRPC), `GrpcMethodPolicy`, servlet + reactive resource
  security, `MqttSecurityVerifier` (HMAC-SHA256, topic-bound, constant-time compare,
  all toggles default-off) (FACT).
- Tests: 5 (JWT verifier, issuer, identity, security properties, MQTT verifier) (FACT).
- **Verdict: PRODUCTION_IMPLEMENTATION** — ISSUE-01/02 envelope lives here and is a
  real app-level mechanism (FACT, matches recorded remediation direction).

**libs/smartfarm-messaging** — 0 nodes in graph (graph predates module) (FACT).
- Source (FACT): `DispatchFailureClassifier` + `DefaultDispatchFailureClassifier`
  (Paho-aware TRANSIENT/PERMANENT/UNKNOWN taxonomy), `DispatchFailureDecision`,
  `DispatchFailureCategory`, `DispatchErrorCodes`, `ExponentialBackoff`;
  `MqttClientFactory` (single Paho construction point, FILE-mode fail-fast),
  `MqttConsumerSettings` (rejects `cleanSession=false`+MEMORY hybrid), `PersistenceMode`.
- Tests: 2 (`DispatchClassifierTest`, `MqttConsumerSettingsTest`) (FACT).
- Consumers (FACT): used by `OrderChangedConsumer`, `OrderOutboxRelay`,
  `IdentityMqttCommandDispatcher`, inventory subscriber (per ISSUE-04/05/09).
- **Verdict: PRODUCTION_IMPLEMENTATION** — newly extracted shared infra, consumed
  across order/identity/inventory; graph silence is a staleness artifact, not death
  (INFERENCE, with FACT consumer references).

**libs/smartfarm-proto** — 1 node; inbound 11 / outbound 0 (FACT).
- Generated protobuf/gRPC stubs (common/order/livestock/health/reporting/identity
  message + service types), consumed everywhere (FACT: referenced by gateway,
  services, simulator).
- **Verdict: PRODUCTION_IMPLEMENTATION (generated)** — contract module; low node
  count is because generated sources are not graphed as hand-written symbols
  (INFERENCE).

---

### CHUNK-02 — services/smartfarm-identity (1144 nodes)

- Entry points (FACT): REST `AuthController` (9 mappings); 4 gRPC services —
  `IdentityDirectoryGrpcService`, `IdentityCredentialGrpcService`,
  `IdentityAdministrationGrpcService`, `PlatformAuthorizationAdministrationGrpcService`
  (summary counted 14 GrpcService annotations across stubs+impls); MQTT command
  path `IdentityMqttCommandDispatcher` + `IdentityMqttConnectionManager`; scheduled
  `IdentityOutboxRelay` + `IdentityMqttInboxMaintenance`.
- Persistence (FACT): Flyway not present here but rich JPA domain — tenant
  (`TenantEntity`, `TenantMembershipEntity`), accounts (`UserAccountEntity`,
  `UserProfileEntity`), authorization (`RoleEntity`, `PermissionEntity`,
  `RolePermissionEntity`, `MembershipRoleEntity`, `MembershipFarmEntity`), MFA
  (`MfaChallengeEntity`, `UserAuthenticatorEntity`, `RecoveryCodeEntity`), tokens
  (`RefreshEntity`), outbox + MQTT inbox entities. 49 `@Entity`, 33 `@Repository`,
  93 `@Service`, 14 `@Configuration` (FACT).
- Auth/tenant/validation (FACT): `AuthorizationService`, `FarmScopeService`,
  `TenantMembershipService`, `GrpcMethodPolicy`; `AuthService` uses a constant-time
  `dummyHash` to defeat user-enumeration timing (FACT — mitigation, not demo code).
- Error handling/idempotency (FACT): `GrpcExceptionMapper`, MQTT inbox dedupe
  (`IdentityMqttInboxEntity`), outbox relay with shared classifier (ISSUE-09).
- Tests: 8 (gRPC service, exception mapper, metadata mapper, method policy,
  authorization, farm scope, tenant membership) (FACT).
- Demo/dead candidates: none load-bearing; dev-only surfaces are profile-gated (FACT).
- **Verdict: PRODUCTION_IMPLEMENTATION** — complete identity/authorization platform
  with multi-protocol entry, real multi-tenant persistence, MFA, and durable
  messaging (INFERENCE from the above FACTs).

---

### CHUNK-03 — services/smartfarm-order (960 nodes)

- Entry points (FACT): gRPC (6 GrpcService), REST admin controllers
  (`OrderSagaAdministrationController`, `OrderOutboxAdministrationController`,
  `OrderProjectionRecoveryController`), scheduled workers
  (`OrderSagaPersistentWorker`, `OrderProjectionGapRecoveryWorker`,
  `OrderOutboxRelay`), MQTT `OrderChangedConsumer` + `OrderMqttPublisher`.
- Persistence (FACT): 11 Flyway migrations `V1..V11` (baseline, saga persistence,
  audit/versions, saga recovery audit, outbox snapshot delivery, projection
  ordering, projection gap recovery, saga lifecycle deadlines, administration
  hardening, granular events, entry-event idempotency). Entities for order/line,
  saga/step, outbox, projection inbox/view, recovery audit/archive/gap.
- Saga (FACT): dead `OrderSagaOrchestrator` is GONE from `src/main` (ISSUE-03);
  live path is persistent, claim-based `OrderSagaPersistentWorker` with forward +
  compensation executors, checkpoints, stale-claim recovery, manual-review
  deadlines — a durable orchestrator-free saga.
- Idempotency/retry (FACT): `OrderChangedConsumer` durable (cleanSession=false,
  FILE persistence, manual ACK, version-guard replay-safe) + gap-recovery backstop;
  `OrderOutboxRelay` uses the shared `DispatchFailureClassifier` for poison handling.
- Tests: 8 (saga/step hardening, projection gap/state hardening, draft validation,
  cursor codec, version-conflict, order state machine, domain status) (FACT).
- Demo/dead candidates: `OrderSagaOrchestrator` references remain only in docs and
  the stale graph (FACT) — not in source.
- **Verdict: PRODUCTION_IMPLEMENTATION** — the most hardened service: event-sourced
  CQRS read model, persistent saga, outbox, 11 migrations, idempotency throughout
  (INFERENCE).

---

### CHUNK-04 — apps/smartfarm-gateway (355 nodes)

- Entry points (FACT): REST (`WhoAmIController`, `AuthProxyController`,
  `OrderRestController`), GraphQL controllers (identity/farm/order/order-saga —
  `@QueryMapping`/`@MutationMapping`), MQTT `MqttEventSubscriber` (SSE/event fan-out),
  dev REST facades (`LivestockDevController`, `ReportingDevController`,
  `InventoryDevSetupController`) all `@Profile("dev & !prod")`.
- Persistence (FACT): none — gateway is a stateless aggregation/edge tier
  (927 outbound cross-module edges, 0 inbound).
- Auth (FACT): every mapping carries `@PreAuthorize("hasAuthority('SCOPE_...')")`;
  per-service tokens minted via `ServiceTokenClient` + `BearerCallCredentials`;
  consistent gRPC-status→HTTP-status mapping and `GatewayRestExceptionHandler`.
- Demo/skeleton candidates (FACT, INFERENCE): the `*DevController` classes are
  dev-profile REST facades over gRPC — real auth + error mapping, NOT demo stubs,
  but they are gated off in prod by design (clients go through GraphQL/REST proper).
  RISK: confirm the `dev` profile is never active in a prod deploy.
- **Verdict: PRODUCTION_IMPLEMENTATION** — real multi-protocol edge with uniform
  authz and error translation; dev facades correctly profile-isolated (INFERENCE).

---

### CHUNK-05 — services/smartfarm-inventory (168 nodes)

- Entry points (FACT): 3 gRPC services; MQTT `TaskMqttSubscriber`
  (`@ConditionalOnProperty smartfarm.inventory.mqtt.enabled`, security-verified,
  FILE persistence under `./.local/mqtt-inventory`); `InboxDevController`
  (`@Profile("dev & !prod")`, diagnostics only — count endpoint).
- Persistence (FACT): 2 Flyway migrations (`V1 baseline`, `V2 reorder_threshold`);
  entities Item/Lot/Movement/Balance/Reservation/Inbox; `InventoryRepository`.
- Idempotency (FACT): `InboxEntity` inbox-dedupe pattern present.
- Tests: 0 under `src/test` found (FACT) — gap vs order/identity.
- Demo/dead candidates (FACT): `InboxDevController` is dev-only diagnostics (its own
  javadoc says "remove before production"); `TaskMqttSubscriber` gated off unless
  explicitly enabled.
- **Verdict: PARTIAL_IMPLEMENTATION** — real domain, persistence, gRPC and durable
  MQTT inbox, but RISK: no unit tests found, and the MQTT subscriber is opt-in
  (disabled by default). Core is production-shaped; verification coverage is thin
  (INFERENCE).

---

### CHUNK-06 — services/smartfarm-livestock (216 nodes)

- Entry points (FACT): 3 gRPC services; scheduled `ScheduleTaskGenerator`,
  `TaskDeadlineMonitor`, `OutboxRelay`; MQTT `MqttPublisher`.
- Persistence (FACT): 1 Flyway migration (`V1 livestock_baseline`); entities
  Animal/Task/Schedule/Outbox; `TaskStore`, `OutboxRepository`.
- Lifecycle (FACT): full task lifecycle (create/assign/accept/complete/cancel) with
  accept/report deadline windows (seen in gateway `LivestockDevController` mapping to
  the gRPC contract) and deadline monitoring (`TaskDeadlineMonitor`).
- Tests (FACT): no `src/test` java found, but two integration shell harnesses exist
  (`scripts/livestock-lifecycle-test.sh`, `scripts/livestock-registry-test.sh`).
- Demo/dead candidates: none load-bearing in source (FACT).
- **Verdict: PRODUCTION_IMPLEMENTATION** — real scheduled task domain with deadline
  SLAs, outbox, gRPC contract; INFERENCE. RISK: unit-test coverage is via shell
  harnesses rather than JUnit.

---

### CHUNK-07 — services/smartfarm-health (99 nodes)

- Entry points (FACT): 3 gRPC services; scheduled `HealthOutboxRelay`; MQTT
  `PahoHealthEventPublisher`.
- Persistence (FACT): JPA via `HealthStore` facade + `ObservationJpaRepository` /
  `VaccinationJpaRepository` / `HealthOutboxJpaRepository`; **schema generated by
  Hibernate (no Flyway)** — payloads stored as serialized protobuf bytes; tenant +
  idempotency-key uniqueness per aggregate.
- Idempotency (FACT): read-by-idempotency-key on both observation and vaccination.
- Tests: 1 (`HealthOutboxRelayTest`) (FACT).
- Demo/dead candidates: none in source (FACT).
- **Verdict: PARTIAL_IMPLEMENTATION** — real gRPC + outbox + idempotent JPA store,
  but RISK: relies on Hibernate `ddl-auto` instead of versioned Flyway migrations
  (every other persistent service uses Flyway), and test coverage is a single
  outbox test. Functionally production-shaped; schema-management and verification
  are below the platform bar (INFERENCE).

---

### Cross-cutting observations

- **FACT**: No TODO/FIXME/stub/placeholder debt in any `src/main` java file across
  the platform (grep over all modules returned only a timing-attack `dummyHash` and
  test-only `UnsupportedOperationException`). This is a strong production signal.
- **FACT**: `OrderSagaOrchestrator` (the ISSUE-03 dead path) exists only in docs and
  the stale graph, never in current source.
- **RISK**: Flyway coverage is uneven — order (11), inventory (2), livestock (1),
  finance (1); identity and health rely on JPA/Hibernate schema generation. For a
  production bar, identity and health should adopt versioned migrations.
- **RISK**: inventory has no JUnit tests; livestock's coverage is shell-based.
- **UNKNOWN**: runtime wiring of `@Profile("dev & !prod")` surfaces in each deploy
  environment is not determinable from static read — confirm prod never enables `dev`.
