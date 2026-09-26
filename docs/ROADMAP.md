# Lộ trình phát triển

## Phase 0 - Starter hiện tại

- Workspace composite, manifest clone, source skeleton, H2/dev và PostgreSQL/prod.
- Proto v1, gRPC sample, Gateway GraphQL sample, readiness/simulator API, diagrams.

## Phase 1 - Nền tảng dùng chung (1-2 sprint)

- Maven BOM/parent POM publish, dependency lock/verification, Maven registry.
- OAuth/OIDC Authorization Server hoặc tích hợp IdP, JWKS rotation, audience validator.
- gRPC correlation/auth/authz/error/observability interceptors và contract tests.
- Flyway baseline, Testcontainers PostgreSQL/Mosquitto.

## Phase 2 - Livestock vertical slice (2 sprint)

- Hexagonal architecture: domain, application, adapters.
- Create/assign/complete recurring task; scheduler with distributed lock.
- Outbox relay, inbox dedup, MQTT topic ACL, idempotency key.
- Gateway REST/GraphQL facade và end-to-end test.

## Phase 3 - Inventory + Health (2-3 sprint)

- Stock ledger và CQRS read model; lot traceability.
- Vaccination/treatment records, abnormal telemetry rules, alert flow.
- Simulator scenarios and replay-safe consumers.

## Phase 4 - Finance + Reporting (2 sprint)

- Double-entry-inspired immutable ledger, cost aggregation by herd/batch.
- Async report job, object storage, PDF/Excel/CSV template strategy.

## Phase 5 - Production hardening

- mTLS, secret manager, MQTT TLS/ACL, PostgreSQL HA, backups.
- OpenTelemetry traces/metrics/log correlation, SLO and alerts.
- Supply-chain security, SBOM, image signing, disaster recovery and load tests.

## Definition of Done mỗi service

Build độc lập; API/proto versioned; migration reproducible; unit/integration/contract tests; authn/authz; idempotency;
health/readiness; telemetry; timeout/deadline; runbook và threat model.
