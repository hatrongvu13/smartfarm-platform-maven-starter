# Sequence tổng quát

## Command tạo nhiệm vụ và saga

```mermaid
sequenceDiagram
  autonumber
  actor Client
  participant GW as Gateway/BFF
  participant LS as Livestock Service
  participant DB as Livestock DB
  participant OB as Outbox Relay
  participant MQ as MQTT
  participant IV as Inventory Service
  participant FN as Finance Service
  Client->>GW: POST /tasks + Bearer JWT
  GW->>GW: Verify JWT, audience, scope
  GW->>LS: gRPC CreateTask + metadata + deadline
  LS->>DB: TX: save Task + OutboxEvent
  DB-->>LS: commit
  LS-->>GW: taskId, CREATED
  GW-->>Client: 202 Accepted
  OB->>DB: poll NEW outbox
  OB->>MQ: smartfarm/domain/task-created/v1
  MQ-->>IV: TaskCreated
  IV->>IV: deduplicate eventId, reserve stock
  IV->>MQ: InventoryReserved/Failed
  MQ-->>FN: InventoryReserved
  FN->>MQ: FinancePosted/Failed
  MQ-->>LS: status events
  LS->>DB: update saga/task status
```

> **Ghi chú khớp code v1 (order saga).** Sơ đồ trên minh hoạ mô hình *choreography* định hướng cho task lifecycle.
> Trong v1, **PlaceOrder saga đã cài đặt theo *orchestration đồng bộ*** bên trong `order-service`: order gọi lần lượt
> `Inventory.Reserve` → `Finance.RecordExpense` → `Inventory.Commit` qua **gRPC** (mỗi bước lỗi → compensation
> `release` + `reverseExpense`), rồi phát `OrderChanged` qua **outbox → MQTT** để dựng read-model CQRS
> (`ord_order_view`). Nghĩa là bước reserve/expense/commit là gRPC trực tiếp (không choreography qua MQTT); MQTT chỉ
> mang **event thông báo trạng thái** cho read-model và realtime. Chi tiết ở
> [docs/GATEWAY-API-V1.md §6](../v1/GATEWAY-API-V1.md#6-inventory--order-saga-apiv1).

## IoT simulator

```mermaid
sequenceDiagram
  participant SIM as Farm Simulator
  participant MQ as MQTT Broker
  participant H as Health Service
  participant A as Alerting Adapter
  SIM->>MQ: smartfarm/devices/{deviceId}/telemetry/v1
  MQ-->>H: temperature/heart-rate/status
  H->>H: validate + deduplicate + evaluate rule
  alt abnormal
    H->>MQ: smartfarm/domain/health-alert-raised/v1
    MQ-->>A: alert event
  end
```

## Readiness

```mermaid
sequenceDiagram
  participant OPS as Operator/Kubernetes
  participant R as Readiness Service
  participant S as All required services
  OPS->>R: GET /api/v1/platform/readiness
  par bounded parallel gRPC Ping
    R->>S: Ping(deadline=500ms)
    S-->>R: UP + version
  end
  R-->>OPS: UP only when required dependency policy passes
```
