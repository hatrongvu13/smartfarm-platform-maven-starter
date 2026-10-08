# Documentation Index — SmartFarm Platform

> Cổng điều hướng tài liệu. Verified @ HEAD `eaaa112` (2026-10-06). Nguồn sự thật: **source code** (xem từng doc để biết bằng chứng).

## Bắt đầu
- [README (project overview + quick start)](../README.md)
- [Roadmap theo version](roadmap.md)
- [PROJECT STATUS (completion matrix)](PROJECT-STATUS.md)

## Kiến trúc
- [System Overview + 8 sơ đồ Mermaid](architecture/system-overview.md)
- [Gateway Mapping (route table + gaps)](architecture/gateway-mapping.md)
- [Request Flows (login/register/refresh/saga/event)](architecture/request-flows.md)
- [Order Saga](architecture/order-saga.md)

## API
- [REST API](api/rest-api.md)
- [gRPC API (traceability matrix)](api/grpc-api.md)
- [Events (MQTT)](api/events.md)

## Services
- [gateway](services/gateway.md) · [identity](services/identity.md) · [order](services/order.md) · [inventory](services/inventory.md) · [finance](services/finance.md) · [livestock](services/livestock.md) · [health](services/health.md) · [reporting](services/reporting.md)

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
- [SECURITY.md (policy)](../SECURITY.md) · [LICENSE-TODO](../LICENSE-TODO.md)

## Audit (đợt 2026-10-06)
- [Documentation Audit](audit/documentation-audit.md)
- [Unresolved Items (active issues + bằng chứng)](audit/unresolved-items.md)
- [Outstanding Issues (LARGE — need confirmation, code untouched)](audit/outstanding-issues.md)
- [Removed / Archived Files](audit/removed-or-archived-files.md)
- [Runtime Readiness](07-runtime-readiness.md)
- [Implementation Roadmap](10-implementation-roadmap.md) · [Task Dependency Graph](11-task-dependency-graph.md)
- Trước đó: [audit/00-08](audit/00-executive-summary.md), [cleanup/00-12](cleanup/00-executive-summary.md), [issue register](audit/04-issue-register.md), [graphify](audit/graphify/snapshot-v0.md)

## History
- [Resolved Issues](history/resolved-issues.md)
- [Deprecated Documentation](history/deprecated-documentation.md)

## Backlog
- [Backlog README](../backlog/README.md) · [V1](../backlog/V1-Core-Platform/00-README.md) · [V2](../backlog/V2-Warehouse-Operations/00-README.md) · [V3](../backlog/V3-Connected-Farm/00-README.md)
