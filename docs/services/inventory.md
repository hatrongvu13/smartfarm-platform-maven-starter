# Service: Inventory (`services/inventory-service`)

> Reconciled 2026-10-15 from the proto and `InventoryGrpcService`.

## Responsibility and interfaces

Inventory manages items, warehouses, stock movements/balances, reservations, low-stock queries, and lot tracing. Its gRPC server is configured at port `9093`.

All 20 methods declared by `InventoryService` have handlers, including `AdjustStock`, `TraceLot`, item and warehouse CRUD/status methods. Earlier documentation claiming `AdjustStock` and `TraceLot` were absent is stale. Handler presence does not imply every method has a gateway caller or production REST route.

- Order saga calls `ReserveStock`, `CommitReservation`, and `ReleaseReservation`.
- The gateway inventory REST facade is development-profile-only and covers only the routes declared by its controllers.
- `TaskMqttSubscriber` subscribes to `task-changed/v1`; it is not a general consumer for every livestock lifecycle event.

## Persistence

Inventory owns relational persistence and the migrations under `services/inventory-service/src/main/resources/db/migration/`. Check that directory and its active profile configuration for exact schema/database behavior.

## Remaining work

- [ ] Verify callers, authorization, and tests for every stock adjustment/lot-trace operation.
- [ ] Decide whether to widen the MQTT subscription beyond task events and add corresponding idempotency/consumer tests.

[gRPC API](../api/grpc-api.md) · [Order saga](../architecture/order-saga.md) · [System overview](../architecture/system-overview.md)
