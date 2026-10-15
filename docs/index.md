# Documentation Index — SmartFarm Platform

> Cổng điều hướng các tài liệu hiện có trong checkout. Reconciled 2026-10-15; implementation and configuration in source code are authoritative. Verify test/runtime claims separately.

## Bắt đầu
- [README (project overview + quick start)](../README.md)
- [Roadmap theo version](roadmap.md)
- [Identity implementation backlog](../IDENTITY_BACKLOG_UPDATED.md)

## Kiến trúc
- [System Overview + 8 sơ đồ Mermaid](architecture/system-overview.md)
- [Gateway Mapping (route table + gaps)](architecture/gateway-mapping.md)
- [Request Flows (login/register/refresh/saga/event)](architecture/request-flows.md)
- [Service Communications matrix](architecture/service-communications.md)
- [Project Structure Baseline](architecture/project-structure-baseline.md)
- [Order Saga](architecture/order-saga.md)
- [Dynamic Authorization design](architecture/dynamic-authorization/README.md)

## API
- [REST API](api/rest-api.md)
- [gRPC API (traceability matrix)](api/grpc-api.md)
- [Events (MQTT)](api/events.md)

## Services
- [gateway](services/gateway.md) · [identity](services/identity.md) · [Identity flows — target vs current](services/identity-flows.md) · [order](services/order.md) · [inventory](services/inventory.md) · [finance](services/finance.md) · [livestock](services/livestock.md) · [health](services/health.md) · [reporting](services/reporting.md)

## Operations
- [Local Development](operations/local-development.md)
- [Configuration](operations/configuration.md)
- [Deployment](operations/deployment.md)
- [Observability](operations/observability.md)
- [Troubleshooting](operations/troubleshooting.md)
- [Order Runbook](operations/ORDER-OPERATIONS-RUNBOOK.md) · [Order SLO](operations/ORDER-SLO.md) · [Order Prod Acceptance](operations/ORDER-PRODUCTION-ACCEPTANCE.md)

## Security
- [Security Architecture](security/security-architecture.md)
- [Threat Model](security/threat-model.md)
- [MQTT ACL/mTLS (sample)](security/mqtt-acl-mtls.md)
- [SECURITY.md (policy)](../SECURITY.md) · [LICENSE (PolyForm Strict 1.0.0)](../LICENSE) · [COPYRIGHT](../COPYRIGHT)

## Source of truth
- Runtime behavior, configuration, and implemented capabilities: current application source and tests.
- Identity work items and ranked next changes: [Identity implementation backlog](../IDENTITY_BACKLOG_UPDATED.md).
- Historical audit/backlog snapshots referenced by older revisions are not present in this checkout and are intentionally not linked here.
