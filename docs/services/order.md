# Service: Order (`services/smartfarm-order-service`)

> Verified @ HEAD `eaaa112`. :8085 / gRPC :9095. **Most complete service.** ← [System Overview](../architecture/system-overview.md)

## Trách nhiệm
Order lifecycle (draft→place→cancel) + **persistent saga orchestration** (reserve stock → record expense → commit, với compensation), outbox at-least-once, CQRS read-model projection + gap recovery.

## Inbound interface
- **gRPC**:
  - `FarmOrderService`: PlaceOrder, GetOrder, ListOrders, CancelOrder, CreateDraftOrder, UpdateDraftOrder, DeleteDraftOrder, SubmitDraftOrder
  - `OrderSagaAdministrationService`: GetOrderSaga, RetryOrderSagaStep, ResumeOrderSaga, ForceCompensate/Cancel/CompleteOrder, MarkOrderSagaResolved
- **REST admin controllers** (service-internal `/internal/*`): outbox administration, saga administration, projection recovery.

## Outbound dependency (cross-service saga, verified)
- gRPC → **inventory**: ReserveStock, CommitReservation, ReleaseReservation
- gRPC → **finance**: RecordExpense, ReverseExpense

## Event
- Publish: `OrderChanged` protobuf DomainEvent (outbox relay → MQTT).
- Consume: `OrderChangedConsumer` (CQRS read-model, FILE persistence + manual ACK — ISSUE-05 resolved).

## Database
11 migrations (V1 baseline → V11): saga persistence, audit/versions, recovery audit, outbox snapshot delivery, projection ordering + gap recovery, lifecycle deadlines, admin hardening, granular events, event idempotency.

## Health / observability
Outbox health indicator; operational metrics; full Prometheus alerts + Grafana dashboard (observability/). Runbook: `docs/operations/ORDER-OPERATIONS-RUNBOOK.md`, SLO: `ORDER-SLO.md`.

## Known limitations
- `OrderMqttPublisher` cleanSession=true + random client-id (ISSUE-10).
- gRPC deadline 5s cố định (ISSUE-13).

[Order saga flow](../architecture/request-flows.md#4-placeorder--cross-service-saga-gateway--order--inventory--finance) · [order-saga.md](../architecture/order-saga.md)
