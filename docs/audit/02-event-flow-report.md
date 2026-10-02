# 02 — Event-Flow Report (Báo cáo luồng sự kiện)

> Đối chiếu chuỗi topic trong source thật (producers + consumers) toàn nền tảng.

## 1. Bảng Producer / Consumer / Topic

### Producers (FACT)
| Producer | File | Topic pattern | QoS | Transport |
|---|---|---|---|---|
| Order `OrderOutboxRelay` → `OrderMqttPublisher` | `order/outbox/*` | `smartfarm/{tenant}/{farm}/domain/order-changed/v1` (snapshot) và `smartfarm/{tenant}/{farm}/domain/order/{type}/v1` (business) — qua `OrderEventMapper.topic` | 1 | MQTT, cleanSession=true, retained=false |
| Identity `IdentityOutboxRelay` → `IdentityIntegrationEventPublisher` | `identity/messaging/*` | `smartfarm/{tenant}/_global/domain/{aggregate}-{event}/v1` — qua `IdentityEventTopics.domain` | (outbox) | MQTT |
| Health `HealthOutboxRelay`/`PahoHealthEventPublisher` | health-service | `smartfarm/.../domain/...` (ngoài scope sâu — UNKNOWN chi tiết) | — | MQTT |
| Livestock `OutboxRelay`/`MqttPublisher` | livestock-service | `smartfarm/.../domain/...` (ngoài scope sâu — UNKNOWN chi tiết) | — | MQTT |

### Consumers (FACT)
| Consumer | File | Topic filter subscribe | Persistence / Acks |
|---|---|---|---|
| Gateway WS bridge `MqttEventSubscriber` | `gateway/events/MqttEventSubscriber` | `smartfarm/+/+/domain/+/+` (`smartfarm.events.mqtt.topic-filter`) | MemoryPersistence, cleanSession=true (phù du, chỉ forward lên WebSocket) |
| Order read-model `OrderChangedConsumer` → `OrderViewProjector` | `order/readmodel/*` | `smartfarm/+/+/domain/order-changed/+` (`...readmodel.topic-filter`) | **MemoryPersistence + cleanSession=false (mâu thuẫn — ISSUE-05)** |
| Inventory `TaskMqttSubscriber` → `TaskInbox` | `inventory/mqtt/*` | `smartfarm/+/+/domain/task-changed/v1` | **MqttDefaultFilePersistence + manualAcks (chuẩn durable)** |
| Identity command inbox `IdentityMqttCommandIntake` | `identity/messaging/command/*` | `smartfarm/+/identity/command/+/v1` (`...commands.topic-filter`) | Inbox DB + dedup (idempotent — ISSUE-17) |

## 2. Khớp Producer ↔ Consumer

- **order-changed**: producer `smartfarm/{t}/{f}/domain/order-changed/v1` ✔ khớp:
  - Gateway filter `smartfarm/+/+/domain/+/+` → match (forward WS).
  - Order read-model filter `smartfarm/+/+/domain/order-changed/+` → match. ✔
- **order business lifecycle** (`.../domain/order/{type}/v1`): Gateway `+/+/domain/+/+` **KHÔNG match** đoạn `domain/order/{type}/v1` (6 segment vs filter 6 segment `smartfarm/+/+/domain/+/+` = `smartfarm`,`t`,`f`,`domain`,`+`,`+`). Topic business có **7 segment** (`smartfarm/t/f/domain/order/{type}/v1`) ⇒ **không khớp gateway filter** (RISK/INFERENCE — cần xác nhận bằng broker thực). Read-model chỉ quan tâm `order-changed` nên không ảnh hưởng projection. → **Business lifecycle event có thể không tới WS** (hở quan sát).
- **task-changed**: chỉ inventory tiêu thụ; producer task-changed nằm ngoài scope sâu (livestock/inventory) — UNKNOWN producer chính xác.
- **identity domain events** (`smartfarm/{t}/_global/domain/{agg}-{event}/v1`): Gateway `+/+/domain/+/+` → `smartfarm`,`t`,`_global`,`domain`,`{agg}-{event}`,`v1` = **6 segment, match** ✔ (forward WS). Không có consumer nghiệp vụ.
- **identity.command.result** (`.../_global/domain/identity-result/v1`): **orphan producer** — không consumer nghiệp vụ (ISSUE-06). Gateway có thể forward lên WS nhưng không có xử lý.

## 3. Producer không consumer / Consumer không producer
- **Orphan producer**: `identity.command.result` (ISSUE-06). Order **business lifecycle** events (`domain/order/{type}/v1`) có thể orphan ở gateway do filter không match (RISK — ISSUE mới cần xác nhận, xem mục 2).
- **Consumer không producer**: không phát hiện trong scope (mọi consumer đều có producer tương ứng).

## 4. Lệch tên topic (topic-name mismatch)
- Gateway filter `smartfarm/+/+/domain/+/+` giả định **đúng 6 segment**; nhưng order business topic là **7 segment** (`.../domain/order/{type}/v1`). Đây là **mismatch số segment** (RISK). Cần: hoặc đổi filter gateway thành `smartfarm/+/+/domain/#`, hoặc chuẩn hoá topic business về 6 segment.

## 5. Idempotency & đảm bảo giao hàng (delivery guarantee)
- **At-least-once**: outbox DB (order/identity) + QoS1 ⇒ at-least-once. Yêu cầu consumer idempotent.
- **Consumer idempotency**:
  - Order read-model: dedup theo `eventId` (`inbox.existsById`) + bỏ version cũ + ordered drain theo `aggregateVersion` ⇒ **idempotent + replay-safe** (ISSUE-18). ✔
  - Inventory: `TaskInbox.accept` + manual acks (ack sau commit DB) ⇒ at-least-once đúng. ✔
  - Identity command: dedup `commandId` + unique constraint ⇒ idempotent. ✔
  - Gateway WS: chỉ forward, không trạng thái ⇒ trùng lặp vô hại.
- **Thiếu sót**:
  - ISSUE-04/09: relay/dispatcher `break` khi lỗi ⇒ head-of-line blocking (ảnh hưởng độ trễ, không mất dữ liệu).
  - ISSUE-05: order consumer persistence mâu thuẫn ⇒ có thể sót QoS1 khi restart, được gap-recovery bù.
  - ISSUE-02: không có xác thực/toàn vẹn message ⇒ không chống event giả mạo.

## 6. Sơ đồ (text)
```
order-service ──order-changed.v1──> MQTT ──> order read-model (OrderView, idempotent)
                                         └──> gateway WS bridge ──> WebSocket clients
order-service ──order/{type}.v1────> MQTT ──> (gateway filter MISMATCH? RISK) 
identity ──{agg}-{event}.v1────────> MQTT ──> gateway WS bridge (observe only)
identity ──identity-result.v1──────> MQTT ──> (ORPHAN: no business consumer)  [ISSUE-06]
inventory ──(task producer?)───────> MQTT ──> inventory TaskInbox (durable, manual acks)
```

## 7. Khuyến nghị
- Chuẩn hoá filter gateway `smartfarm/+/+/domain/#` để bắt mọi độ sâu topic (giải quyết mismatch 6 vs 7 segment).
- Khép vòng `identity.command.result` (consumer hoặc tài liệu observation-only).
- Thêm xác thực message (ISSUE-02) + đồng nhất persistence consumer (ISSUE-05).
