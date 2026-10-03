# Module Maturity Register — Part 2

PHASE 2 module-maturity assessment, continuation of `02-module-maturity-register.md`.
Verdicts are grounded in source read directly (entry points, persistence, auth/tenant
scope, error handling, config, tests). `graphslice.py` located candidates; the
cross-service gRPC edges are **not** in the dependency graph (stubs live in the proto
module), so CHUNK-11 is confirmed by reading service source directly.

Each line is tagged **FACT** (read in source / counted from the graph),
**INFERENCE** (reasoned from evidence), **UNKNOWN** (not determinable from static
read), **RISK**, or **BLOCKER**.

Prior remediation already in the tree is NOT re-flagged as a defect: ISSUE-01/02
(MQTT HMAC envelope + verifier), ISSUE-03 (dead `OrderSagaOrchestrator` removed),
ISSUE-04/05/09 (`libs/smartfarm-messaging` shared classifier + MQTT factory, outbox
poison handling, durable consumer, identity dispatcher classifier).

Maturity scale: `PRODUCTION_IMPLEMENTATION` / `PARTIAL_IMPLEMENTATION` /
`DEMO_OR_SKELETON` / `STALE_OR_DEAD` / `UNKNOWN`.

## Part 2 — CHUNK 08-13 (finance, reporting, platform/simulator, cross-gRPC, MQTT/event-flow, scripts/config/docs)

### Verdict summary

| Chunk | Module | Nodes | Verdict |
|---|---|---|---|
| 08 | services/smartfarm-finance-service | 89 | PRODUCTION_IMPLEMENTATION (with prod-config BLOCKER) |
| 09 | services/smartfarm-reporting-service | 111 | PARTIAL_IMPLEMENTATION |
| 10 | platform/smartfarm-readiness-service | 31 | PRODUCTION_IMPLEMENTATION |
| 10 | platform/smartfarm-farm-simulator | 12 | DEMO_OR_SKELETON |

---

### CHUNK-08 — services/smartfarm-finance-service (89 nodes)

- Entry points (FACT): 1 gRPC service `FarmFinanceGrpcService` (10 RPCs:
  RecordExpense, ReverseExpense, RecordIncome, GetTransaction, ListTransactions,
  RecordPayable, RecordReceivable, SettleDebt, GetBatchCost, GetCashFlow). No REST,
  no MQTT. Annotations: {Entity:6, Service:5, Repository:4, GrpcService:3,
  Configuration:2}.
- Persistence (FACT): 1 Flyway migration `V1__finance_baseline.sql` creating
  `fin_transaction` and `fin_debt`; JPA entities `TransactionEntity`, `DebtEntity`;
  repos `TransactionJpaRepository` (JPQL for list / sum-by-kind / sum-by-category /
  cash-flow aggregates), `DebtJpaRepository`. Money is integer minor units + ISO-4217
  currency — no floating point.
- Idempotency (FACT): every write command keyed on `(tenant_id, idempotency_key)`
  with a DB unique constraint AND a read-prior-and-compare guard; a reused key with a
  different amount is rejected. `reverseExpense` is the saga compensation path
  (idempotent on the reversal key, mirrors the original as an offsetting entry).
- Auth/tenant (FACT): tenant taken from the verified gRPC call context
  (`GrpcSecurityContext.TENANT`), never the request body; `FinanceGrpcPolicyConfiguration`
  enforces per-method `SCOPE_finance:write` / `SCOPE_finance:read`.
- Error handling (FACT): `respond()` maps IllegalArgument → INVALID_ARGUMENT,
  DataIntegrityViolation → ALREADY_EXISTS, other → INTERNAL.
- Tests: 0 under `src/test` (FACT) — gap.
- **BLOCKER (FACT)**: `application-prod.yml` sets `spring.jpa.hibernate.ddl-auto:
  create-drop`. In prod this **drops and recreates the entire finance schema on every
  startup** — total data loss on restart. Every other persistent service uses
  `validate` in prod. Flyway is also enabled with `baseline-on-migrate`, so the two
  schema managers additionally conflict. This single line disqualifies the prod
  profile until fixed.
- **Verdict: PRODUCTION_IMPLEMENTATION** of the service logic (real ledger, debt,
  batch-cost + cash-flow aggregates, idempotent saga-compensating writes, per-method
  authz, Flyway V1) — **but the prod config carries a data-loss BLOCKER** and there
  are no unit tests (INFERENCE from the above FACTs).

---

### CHUNK-09 — services/smartfarm-reporting-service (111 nodes)

- Entry points (FACT): 1 gRPC service `ReportingGrpcService` (RequestExport,
  GetExportJob, ListExportJobs, GetDownloadLocation); 1 scheduled `ExportJobWorker`
  (`@Scheduled` poll of QUEUED jobs, `@ConditionalOnProperty ...worker.enabled`
  default true). Annotations: {Service:8, Entity:3, GrpcService:3, Repository:2,
  Configuration:2}.
- Cross-service data (FACT): `ReportDataProvider` pulls REAL data for
  `REPORT_TYPE_LIVESTOCK_TASKS` from the **livestock service over gRPC**
  (`LivestockTaskServiceBlockingStub`), authenticated with a per-service token
  (`ServiceTokenClient` → identity `/internal/service-token`, audience
  `smartfarm-livestock`) + `BearerCallCredentials`. All OTHER report types fall to a
  `summary()` descriptive table (**not** real source data yet) — see RISK below.
- Persistence (FACT): JPA entity `ExportJobEntity` (`rpt_export_job`), repo
  `ExportJobJpaRepository`; idempotent on `(tenant_id, idempotency_key)`. Job
  lifecycle QUEUED→RUNNING→COMPLETED/FAILED.
- Rendering (FACT): `ReportRenderer` writes CSV (hand-rolled RFC-4180 quoting), XLSX
  (Apache POI), PDF (OpenPDF with a Unicode TrueType base font + embedding for
  Vietnamese; careful stream-close ordering). This is real, non-trivial code.
- Auth/tenant (FACT): tenant from verified call context; `ReportingGrpcPolicyConfiguration`
  per-method `SCOPE_report:write` / `SCOPE_report:read`.
- **RISK (FACT)**: NO Flyway migrations and `application-prod.yml` has **no**
  `ddl-auto` override — the prod profile inherits the dev default (`update`), i.e.
  Hibernate-generated schema in production. Entity javadoc states "Schema generated
  by Hibernate (ddl-auto)". Same gap flagged for health in Part 1, plus prod lacks a
  versioned schema entirely.
- **RISK (FACT)**: `GetDownloadLocation` returns a `file://<local path>` URL (the
  code's own comment: "Production returns a presigned object-storage URL"). The
  artifact store is a local directory (`./.local/reports`), single-instance worker —
  not horizontally scalable or durable object storage.
- **RISK (INFERENCE)**: non-livestock report types render only a placeholder summary
  row, so most `ReportType` values are not yet wired to a real read model.
- Tests: 0 under `src/test` (FACT).
- **Verdict: PARTIAL_IMPLEMENTATION** — real gRPC + idempotent job model + genuine
  multi-format rendering + ONE real cross-service data source (livestock), but prod
  schema management is Hibernate-only (no Flyway), the download location is a local
  `file://` reference rather than object storage, most report types are placeholder
  summaries, and there are no tests (INFERENCE).

---

### CHUNK-10 — platform/smartfarm-readiness-service (31 nodes)

- Entry points (FACT): 1 gRPC service `ReadinessGrpcService`
  (`GetPlatformReadiness`); 1 REST `ReadinessController`
  (`GET /api/v1/platform/readiness`); scheduled `ReadinessMonitor.refresh()`.
  Annotations: {Service:5, Configuration:4, GrpcService:3}.
- Behaviour (FACT): `ReadinessMonitor` polls a bounded, validated list of health
  URLs (`ProbeSettings`: 1..20 targets, 1ms..5s timeout, HTTPS-or-explicit-loopback,
  no userinfo/query/fragment, redirects disabled) and caches an
  `AtomicReference<Snapshot>`. It **never probes a caller-supplied URL** (SSRF-safe by
  design) and returns NOT_READY when the snapshot is older than `maxAge`.
- Auth/tenant (FACT): gRPC `GetPlatformReadiness` requires `SCOPE_platform:read` and
  verifies tenant from the call context; HTTP chain is STATELESS JWT resource server,
  readiness endpoint gated on `SCOPE_platform:read`, actuator health permitted,
  everything else `denyAll()`. A startup `ApplicationRunner` refuses `allowLocalHttp`
  outside a `dev & !prod` profile.
- Config (FACT): `application-prod.yml` wires 4 probe targets (identity, gateway,
  livestock, inventory) from env `*_HEALTH_URL`; `allow-local-http: false`.
- Tests: 0 under `src/test` (FACT).
- **Verdict: PRODUCTION_IMPLEMENTATION** — small but complete and defensively
  written: bounded SSRF-safe probing, cached snapshot with staleness, dual gRPC+REST
  surface with proper authz, env-driven prod targets. RISK: no unit tests (INFERENCE).

---

### CHUNK-10 — platform/smartfarm-farm-simulator (12 nodes)

- Entry points (FACT): `FarmSimulatorApplication` (`web-application-type: none` per
  yml, yet it ships a `@RestController` — see note); `SimulationController`
  (`POST /api/v1/simulations/{type}`); `TaskEventObserver` (MQTT subscriber).
  Annotations: {} (none counted by the graph).
- `SimulationController` (FACT): returns a **hard-coded `202 Accepted` JSON stub**
  `{topic, type, at}` and publishes nothing. Its own comment: "Adapter boundary:
  replace body with MqttEventPublisher in the next increment."
- `TaskEventObserver` (FACT): subscribes to `smartfarm/+/+/domain/task-changed/v1`
  (QoS 1, `MemoryPersistence`, cleanSession=true) and just LOGS observed task events.
  Its own javadoc: "Local diagnostic subscriber; not a durable inbox or business
  consumer." It does NOT verify the MQTT HMAC envelope (unlike every production
  consumer), consistent with being a dev diagnostic.
- Config note (RISK, FACT): `application.yml` declares
  `spring.main.web-application-type: none` while the module also declares a
  `@RestController` — the REST endpoint will not be served under that setting, so the
  simulate-via-HTTP path is effectively dead as configured. No security config, no
  persistence, no tests.
- **Verdict: DEMO_OR_SKELETON** — a placeholder: the HTTP "emit" is a stub that
  publishes nothing, the only live behaviour is a diagnostic log-only subscriber, and
  the web layer is disabled by config. It is a scaffold for a future device simulator,
  not a working component (INFERENCE from the above FACTs). Candidate to either finish
  (wire a real `MqttClientFactory` publisher + remove `web-application-type: none`) or
  quarantine as explicitly dev-only.

---

### CHUNK-11 — Cross-service gRPC call map (confirmed from SOURCE)

The dependency graph does **not** capture service-to-service gRPC edges (the stubs
live in `libs/smartfarm-proto`, so callers show only lib edges in `--crossgrpc`).
The map below is built by reading `*Grpc`, `*BlockingStub`, `ManagedChannel` and
`@GrpcService` usage in service source (grep + file read). There are **no**
`@GrpcClient` annotations anywhere — all channels are hand-wired beans. (FACT)

**Services DEFINED (proto `service` blocks) and their server impls:**

| Proto service (package) | Server impl (module) |
|---|---|
| `smartfarm.farm.v1.FarmDirectoryService` | *(no server impl found — see RISK)* |
| `smartfarm.finance.v1.FarmFinanceService` | finance `FarmFinanceGrpcService` |
| `smartfarm.health.v1.AnimalHealthService` | health `HealthGrpcService` |
| `smartfarm.identity.v1.IdentityDirectoryService` | identity (4 impls total) |
| `smartfarm.identity.v1.IdentityAdministrationService` | identity |
| `smartfarm.identity.v1.PlatformAuthorizationAdministrationService` | identity |
| `smartfarm.identity.v1.IdentityCredentialService` | identity |
| `smartfarm.inventory.v1.InventoryService` | inventory `InventoryGrpcService` |
| `smartfarm.livestock.v1.LivestockTaskService` | livestock `LivestockGrpcService` |
| `smartfarm.order.v1.FarmOrderService` | order `FarmOrderGrpcService` |
| `smartfarm.order.v1.OrderSagaAdministrationService` | order |
| `smartfarm.readiness.v1.PlatformReadinessService` | readiness `ReadinessGrpcService` |
| `smartfarm.reporting.v1.ReportingService` | reporting `ReportingGrpcService` |

**Caller → callee edges (who holds a stub for whom):**

| Caller (module) | Callee gRPC service | Where (FACT) | Profile |
|---|---|---|---|
| gateway | IdentityDirectory / Administration / Credential / PlatformAuthz | `identity/IdentityGrpcClientConfig`, `graphql/IdentityGraphQlController` | all |
| gateway | FarmOrderService + OrderSagaAdministration | `order/OrderGrpcClientConfiguration`, `OrderGraphQlController`, `OrderRestController`, `OrderSagaGateway` | all |
| gateway | InventoryService | `order/OrderGrpcClientConfiguration` (gwInventory), `FarmGraphQlController`, `InventoryDevSetupController` | dev for DevSetup |
| gateway | FarmFinanceService | `order/OrderGrpcClientConfiguration` (gwFinance), `FarmGraphQlController` | all |
| gateway | LivestockTaskService | `livestock/LivestockDevClientConfig` + `FarmGraphQlController` | DevController `@Profile(dev & !prod)` |
| gateway | ReportingService | `reporting/ReportingDevClientConfig` + `ReportingDevController` | **`@Profile(dev & !prod)`** |
| order | InventoryService | `order/grpc/OrderGrpcClientConfig`, saga `OrderSagaForwardStepExecutor` / `OrderSagaCompensationStepExecutor` | all |
| order | FarmFinanceService | same saga executors (forward + compensation) | all |
| reporting | LivestockTaskService | `reporting/grpc/ReportingGrpcClientConfig`, `render/ReportDataProvider` | all |

**Notable (RISK/UNKNOWN):**
- **RISK (FACT)**: `FarmDirectoryService` is defined in `farm.proto` but **no server
  `*ServiceImplBase` subclass exists** in any module — the farm directory contract has
  no implementation (dead contract or unbuilt feature). Confirm whether any client
  depends on it.
- **FACT**: The order saga is the only true service-to-service orchestration: order
  calls inventory (reserve/commit/release stock) and finance (record/reverse expense)
  as forward + compensating steps. Reporting→livestock is a read-only data pull.
- **FACT**: reporting's own gRPC client allows plaintext on loopback only (throws
  otherwise); order and gateway channels go through hardened channel factories.

---

### CHUNK-12 — MQTT producer/consumer + topic map; outbox/inbox

Topic convention (FACT): `smartfarm/<tenant>/<farm>/domain/<event>/v1`. Every
production publisher signs with the ISSUE-01/02 HMAC envelope
(`mqttSecurity.sign(topic, payload)`); every production consumer verifies
(`MqttSecurityVerifier.verify`) and drops/quarantines on failure. All publishers are
QoS 1, lazy-connect ("broker outage does not block startup").

**Producers (publishers):**

| Module | Class | Topic(s) produced (FACT) |
|---|---|---|
| order | `OrderMqttPublisher` ← `OrderOutboxRelay` (`OrderEventMapper.topic`) | `smartfarm/<t>/<f>/domain/order-changed/v1` and `smartfarm/<t>/<f>/domain/order/<type>/v1` |
| livestock | `MqttPublisher` ← `OutboxRelay` (`TaskEventMapper.topic`) | `smartfarm/<t>/<f>/domain/<task-event>/v1` (e.g. task-assigned/v1) |
| health | `PahoHealthEventPublisher` ← `HealthOutboxRelay` | `smartfarm/<t>/<f>/domain/observation-recorded/v1` (loopback-only dev publisher) |
| identity | `IdentityMqttCommandDispatcher.publishResult` (via `IdentityEventRecorder`) | command-result publish on the identity command topic family |

**Consumers (subscribers):**

| Module | Class | Topic filter subscribed (FACT) | Durability |
|---|---|---|---|
| order | `OrderChangedConsumer` | `smartfarm/+/+/domain/order-changed/+` (configurable) | durable: cleanSession=false, FILE persistence, manual ACK, replay-safe, envelope-verified |
| inventory | `TaskMqttSubscriber` | `smartfarm/+/+/domain/task-changed/v1` | envelope-verified; inbox-dedupe; ACK withheld on DB failure; `@ConditionalOnProperty` (opt-in) |
| identity | `IdentityMqttCommandIntake` / `IdentityMqttConnectionManager` | `smartfarm/+/identity/command/+/v1` (parsed `IdentityMqttCommandTopic`) | durable inbox (`IdentityMqttInboxEntity`), dedupe, envelope + tenant/type/version validation |
| gateway | `MqttEventSubscriber` | `smartfarm/+/+/domain/+/+` (configurable) | fan-out bridge to in-process `DomainEventBus` → WebSocket (`EventWebSocketHandler`); envelope-verified |
| simulator | `TaskEventObserver` | `smartfarm/+/+/domain/task-changed/v1` | DIAGNOSTIC log-only, cleanSession=true, MemoryPersistence, **no envelope verify** (dev) |

**Outbox / inbox tables (FACT):**

| Module | Outbox | Inbox |
|---|---|---|
| order | `OrderOutboxEntity` (+ relay, admin, health, scheduling, poison classifier) | `OrderProjectionInboxEntity` (read-model projection drain, gap recovery) |
| identity | `IdentityOutboxEntity` (+ relay, admin, health, properties, classifier) | `IdentityMqttInboxEntity` (command dedupe, maintenance, health) |
| livestock | `OutboxEntity` (+ relay, scheduling, `TaskEventMapper`) | — (none; livestock only publishes) |
| health | `HealthOutboxEntity` (+ relay, scheduling) | — |
| inventory | — (only consumes) | `InboxEntity` (`TaskInbox` dedupe) |

- **FACT**: order and identity have the most complete transactional-outbox + inbox
  stacks (relay, poison handling via `libs/smartfarm-messaging`
  `DispatchFailureClassifier`, health indicators, admin surfaces).
- **FACT**: `libs/smartfarm-messaging` `MqttClientFactory` is the single Paho
  construction point used by the durable consumers/relays (ISSUE-04/05).

---

### CHUNK-13 — scripts / config / docs inventory

**scripts/** (FACT):

| Path | Kind | Note |
|---|---|---|
| `scripts/bootstrap.sh`, `scripts/doctor.sh`, `scripts/build-all.sh` | shell | dev bootstrap / env-doctor / build-all |
| `scripts/livestock-lifecycle-test.sh`, `scripts/livestock-registry-test.sh` | shell | integration harnesses (livestock) |
| `scripts/phase2-curl-test.sh`, `scripts/phase3-saga-curl-test.sh` | shell | phase curl harnesses; some comments/notes in Vietnamese |
| `scripts/dev/curl-06-cancel-completed.sh`, `curl-07-multiline.sh`, `rebuild-restart.sh` | shell | ad-hoc dev curls / rebuild loop |
| `scripts/release/v1/00..60-*.sh`, `_lib.sh`, `run-all.sh` | shell | structured V1 release validation suite (preflight → auth → livestock → saga → reporting → graphql) |
| `scripts/release/production/validate-order-release.sh` | shell | prod order release validation |

**Top config/infra files (FACT):** `compose.yaml` (postgres/mqtt/redis),
`Dockerfile` (hardened multi-stage, non-root uid 1001, OOM flags, healthcheck),
`infra/mosquitto.conf`, `.env.production.example` (names only, no values),
`deploy/kubernetes/order-gateway.yaml` + README, `observability/` (prometheus
`order-alerts.yml` + `prometheus.yml`, grafana order dashboard + provisioning,
`docker-compose.monitoring.yml`).

**Smells (worst first):**
- **BLOCKER (FACT)**: `services/smartfarm-finance-service/src/main/resources/application-prod.yml`
  → `ddl-auto: create-drop`. Prod finance schema is dropped+recreated on every
  restart → data loss. (Also duplicated in the built `target/classes` copy.)
- **RISK (FACT)**: `services/smartfarm-reporting-service/src/main/resources/application-prod.yml`
  has no `ddl-auto` override and the module ships zero Flyway migrations → prod runs
  Hibernate schema generation (dev default `update`).
- **RISK (FACT)**: `infra/mosquitto.conf` → `allow_anonymous true` (+ unauthenticated
  websockets listener 9001). Dev broker is wide open; app-layer HMAC envelope
  mitigates integrity but not broker-level access control. ACL + credentials are
  documented as sample-only (not enabled) per prior remediation notes.
- **RISK (FACT)**: `compose.yaml` postgres uses `POSTGRES_PASSWORD: root` and the
  healthcheck runs `pg_isready -U smartfarm` while the configured superuser is
  `postgres` — a latent healthcheck/user mismatch; dev-only but misleading.
- **FACT (low risk)**: dev secrets committed in `application-dev.yml`
  (`dev-order-service-secret`, `dev-reporting-service-secret`) and
  `GATEWAY_SERVICE_SECRET` default `dev-gateway-service-secret` in
  `phase2-curl-test.sh`. All are dev defaults, env-overridable in prod, and the
  test-script passwords are the literal placeholder
  `replace-with-a-long-random-password`.
- **FACT (hygiene)**: `scripts/dev/*` and `phase2/phase3` curl harnesses hard-code
  `localhost:<port>` (env-overridable). Fine for dev; should not ship as the only
  integration-test path. Several carry Vietnamese inline comments.
- **UNKNOWN**: whether `deploy/kubernetes/order-gateway.yaml` covers all services or
  only order+gateway (named for two of eight) — needs the orchestrator to confirm
  deploy coverage.

---

### Cross-cutting observations (Part 2)

- **BLOCKER**: finance prod `ddl-auto: create-drop` is the single most dangerous line
  found in CHUNK 08-13 — fix to `validate` before any prod use.
- **RISK**: Flyway coverage remains uneven — finance has V1 but a destructive prod
  ddl-auto; reporting and health have NO migrations (Hibernate schema in prod).
- **FACT**: service-to-service gRPC is confined to two real paths — the order saga
  (order→inventory, order→finance, forward + compensating) and reporting→livestock
  (read-only). Everything else enters via the gateway (GraphQL/REST/WS) or MQTT.
- **RISK**: `FarmDirectoryService` proto contract has no server implementation (dead
  or unbuilt).
- **FACT**: the simulator is the only DEMO_OR_SKELETON in this batch; the readiness
  service is small but production-grade.
- **RISK**: no JUnit tests in finance, reporting, or readiness — the whole Part 2
  batch relies on the shell release harnesses for verification.
