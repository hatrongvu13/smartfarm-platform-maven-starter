# Service: Livestock (`services/smartfarm-livestock-service`)

> Verified @ HEAD `eaaa112`. :8081 / gRPC :9091. ← [System Overview](../architecture/system-overview.md)

## Trách nhiệm
Animal registry, task lifecycle (create/assign/accept/complete/cancel), schedule.

## Inbound — gRPC `LivestockTaskService`
CreateTask, Ping, GetTask, ListTasks, AssignTask, AcceptTask, CompleteTask, CancelTask, CreateSchedule, ListSchedules, RegisterAnimal, GetAnimal, ListAnimals. (Ping: 🟡 no caller.)

## Inbound — REST (gateway, dev-only)
`/api/v1/livestock/animals*`, `/schedules*` (`LivestockRegistryController`), `/api/v1/livestock/tasks*` (`LivestockDevController`). Tất cả `@Profile dev&!prod`.
GraphQL: `tasks`, `task` (dev-only).

## Event publish
Outbox relay → MQTT protobuf: `smartfarm/{tenant}/{farm}/domain/task-*/v1` (task-changed + lifecycle leaves). Chỉ `task-changed/v1` được inventory consume.

## Database
`V1__livestock_baseline.sql` (1 migration).

## Known limitations
- REST/GraphQL dev-only, chưa có đường prod.
- 6 lifecycle event leaf không có consumer ngoài WS bridge.

← [Livestock task flow](../architecture/request-flows.md#5-grpc-flow--livestock-task-lifecycle-gateway--livestock--outbox--inventory-subscriber)
