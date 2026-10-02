# Audit Brief — Risk-First Pass (order + identity + security + event-flow)

ROOT: `/Users/jaxmac/Documents/code/my/smartfarm-platform-maven-starter`
Role: Software Architect + Senior Backend Engineer + Code Auditor.
Stack: Spring Boot 4.1.1 / Java 17 Maven monorepo, gRPC (spring-grpc), MQTT (Mosquitto/Paho), Postgres 17, Redis, Flyway. 13 modules, ~914 Java files.

## SCOPE (this pass only — do NOT analyze other modules yet)
1. **services/smartfarm-order-service** (112 files): saga orchestration (`saga/`), outbox (`outbox/`), CQRS read-model projection (`readmodel/`), gap recovery (`readmodel/recovery/`), 11 Flyway migrations in `src/main/resources/db/migration/V1..V11`.
2. **services/smartfarm-identity-service** (147 files): `api/AuthController`, `authorization/` (RBAC roles/permissions/memberships), `mfa/` (TOTP/recovery/challenge), `oauth2/`, `tenant/`, `token/` (refresh + service tokens), `messaging/command` (MQTT command inbox), `messaging/outbox`.
3. **libs/smartfarm-security** (30 files): `JwtServerInterceptor`, `GrpcMethodPolicy`, `TokenVerifier`/`JwtTokenVerifier`, `ReactiveResourceSecurity`/`ServletResourceSecurity`, `MqttSecurityVerifier`, `RsaJwtIssuer`, autoconfigure classes.
4. **EVENT-FLOW platform-wide**: producers — order `OrderOutboxRelay`/`OrderMqttPublisher`, identity `IdentityOutboxRelay`/`IdentityIntegrationEventPublisher`, health `HealthOutboxRelay`/`PahoHealthEventPublisher`, livestock `OutboxRelay`/`MqttPublisher`; consumers — gateway `MqttEventSubscriber`/`DomainEventBus`/`EventWebSocketHandler`, inventory `TaskMqttSubscriber`/`InboxEntity`, order `OrderChangedConsumer`. Build producer/consumer/topic graph; find events with no consumer, consumers with no producer, topic-name mismatches, missing idempotency, delivery-guarantee gaps.

## GROUND TRUTH — graphify graph at ROOT/graphify-out/
- `graph.json`: 4509 nodes / 13727 edges. node id = lowercased path+symbol. edge relations: references(3752)/calls(3496)/contains(680)/imports(3059)/implements(23)/inherits(69)/method(2342)/depends_on(32)/imports_from(88)/case_of(135)/defines(48). Each edge has `source_file` + `source_location` (e.g. `L15`).
- `.graphify_analysis.json`: keys `gods`, `communities`, `cohesion`, `surprises`.
- `manifest.json`: per-file entries.
- CLI `graphify` is on PATH. You MAY run from ROOT: `graphify query "..."`, `graphify god-nodes`, `graphify explain "<Symbol>"`. You MUST ALSO read real source to confirm every claim.
- God-nodes: OrderEntity(deg 94), OrderSagaStepEntity(67), OrderSagaEntity(63), UserAccountEntity(60), TenantMembershipEntity(53), OrderOutboxEntity(50), OrderSagaTransactionService(49), OrderProjectionGapEntity(46), InventoryRepository(53), TaskEntity(53).
- Graph "surprises": farm-simulator→common-kernel & farm-simulator→proto cross-module deps; `GrpcCaller`→`TokenType` bridges identity↔security communities; gateway→common-kernel/proto.

## HARD RULES (strict)
- Evidence only. Never invent a class/method/table/event/topic absent from source.
- Every finding carries: file path + class/interface + method + line (if determinable) + related graph node/edge.
- Tag EVERY assertion: FACT / INFERENCE / UNKNOWN / RISK / BLOCKER.
- **DO NOT MODIFY ANY SOURCE** (.java/.sql/.yml). Analysis only — DESCRIBE suggested patches in the report, never apply them.
- Prefer small, backward-compatible, reversible fixes; never propose full rewrites.
- If data is missing for a sub-area, continue with what you can and list exactly what's missing.

## ISSUE CLASSES to check per scope
- **Correctness**: tx boundaries, race conditions, idempotency, swallowed exceptions, retry-induced duplication, state-machine gaps.
- **Security**: missing/inconsistent gRPC authorization via `GrpcMethodPolicy` + each service's `*GrpcPolicyConfiguration`; tenant isolation; secrets in config/logs; input validation; any path bypassing `JwtServerInterceptor`.
- **Reliability**: timeout/retry/backoff/circuit-breaker/fallback on gRPC + MQTT; outbox at-least-once + consumer idempotency; saga compensation completeness; projection gap recovery correctness.
- **Performance**: N+1, unbounded queries, missing index vs Flyway DDL, long gRPC call chains, high fan-in god-nodes as bottlenecks.
- **Business completeness**: `OrderDomainStatus` vs `OrderEntity` transitions; saga forward + compensation steps. Classify each workflow COMPLETE / PARTIAL / INCONSISTENT / NOT_IMPLEMENTED / UNKNOWN.
- Read ALL `application.yml` + `application-prod.yml` for the 3 scoped services + security lib for config/secret/timeout findings.

## DELIVERABLES — WRITE these files under ROOT/docs/audit/ (create dir; do not only print)
- `00-executive-summary.md` — overall state, completeness, issue counts by severity, top risks, highest-risk module, incomplete flows, next actions.
- `01-architecture-graph-report.md` — actual architecture; layering/boundary violations; circular deps; god-nodes & bottlenecks with subgraph; cross-layer violations; orphans; interpret the "surprises".
- `02-event-flow-report.md` — producer/consumer/topic table; unmatched producers/consumers; idempotency & delivery-guarantee analysis.
- `03-business-workflow-report.md` — order lifecycle + saga; identity auth/RBAC/MFA: actor, trigger, main/alt/failure flow, data writes, events, completeness class.
- `04-issue-register.md` — each finding in this exact template:
  `ISSUE-NN: <title>` with fields — Severity[CRITICAL|HIGH|MEDIUM|LOW|INFO] / Category[BUG|SECURITY|BUSINESS|ARCHITECTURE|PERFORMANCE|RELIABILITY|DATA|INTEGRATION|CONFIGURATION|OBSERVABILITY|MAINTAINABILITY|TECHNICAL_DEBT|DOCUMENTATION] / Confidence[CONFIRMED|HIGH|MEDIUM|LOW|UNKNOWN] / Status / Module / Business capability / Affected files / Affected symbols / Graph nodes / Graph edges / Evidence / Current behavior / Expected behavior / Root cause / Technical impact / Business impact / Security impact / Runtime risk / Recommended solution / Alternative solution / Compatibility considerations / Files to modify / Suggested patch (DESCRIBED, not applied) / Data migration required / Configuration changes / Rollback plan / Verification steps / Remaining risks / Dependencies with other issues.
- `05-improvement-register.md` — file/class/method/module, reason, proposed solution, priority, dependencies, effort estimate.
- `06-remediation-plan.md` — Phase 0 blockers → data/security correctness → event/API closure → integration → performance → reliability/observability → tech debt → docs. Each phase: scope, files, order, risk, compatibility, rollback, verification, done-criteria.

## STYLE
- Prose in **Vietnamese** (the user is Vietnamese). Keep code symbols, file paths, and table headers in **English**.
- Backlog priority order: data-loss/corruption bugs → security holes → core-business interruption → tx/consistency → unclosed event/API flows → integration → performance → reliability/observability → tech debt → docs.
- Do not prioritize cosmetic refactor before functional/business bugs.

## RETURN to the parent (concise)
Files written (absolute paths); total issue count by severity; the single highest CRITICAL finding; coverage (which of the 4 scopes fully covered vs partial); any BLOCKER/UNKNOWN needing the user. Do not edit source.
