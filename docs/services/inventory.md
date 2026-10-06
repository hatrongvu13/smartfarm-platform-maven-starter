# Service: Inventory (`services/smartfarm-inventory-service`)

> Verified @ HEAD `eaaa112`. gRPC :9093. ← [System Overview](../architecture/system-overview.md)

## Trách nhiệm
Quản lý item/stock, reservation (phục vụ order saga), low-stock, lot trace.

## Inbound — gRPC `InventoryService`
CreateItem, ReceiveStock, IssueStock, **AdjustStock (🔴 chưa impl)**, ReserveStock, ReleaseReservation, CommitReservation, GetStockBalance, ListLowStock, **TraceLot (🔴 chưa impl)**.

→ ReserveStock/CommitReservation/ReleaseReservation là **downstream của order saga** (verified).

## Inbound — MQTT
`TaskMqttSubscriber` sub **chỉ** `task-changed/v1` (6 leaf lifecycle khác không consume — EVT scope hẹp).

## Inbound — REST (gateway, dev-only)
`/api/v1/inventory/items`, `/receipts` (`@Profile dev&!prod`).

## Database
`V1__inventory_baseline.sql`, `V2__inventory_reorder_threshold.sql` (2 migrations).

## Known limitations
- AdjustStock, TraceLot: proto-defined, **implementation missing** (PARTIAL).
- MQTT subscriber phạm vi hẹp.

## TODO
- [ ] Impl AdjustStock, TraceLot.

← [Order saga flow](../architecture/request-flows.md#4-placeorder--cross-service-saga-gateway--order--inventory--finance)
