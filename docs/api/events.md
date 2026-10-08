# Events — SmartFarm Platform (MQTT)

> Verified từ outbox/publisher/consumer @ HEAD (updated 2026-10-07, EVT-01/02/03 fixed). Broker: Mosquitto :1883.

## Topic schema (chuẩn)
`smartfarm/{tenantId}/{farmId}/domain/{aggregate}-{event}/v1` — payload = protobuf `DomainEvent`.

## Publishers (outbox relay → MQTT)
| Service | Payload | Topic | Status |
|---------|---------|-------|:------:|
| order | protobuf | `.../domain/order-changed/v1` | 🟢 |
| livestock | protobuf | `.../domain/task-changed/v1` + lifecycle leaves | 🟢 |
| health | protobuf (signed) | `.../domain/health-*/v1` | 🟢 all-profile (EVT-03 fixed) |
| identity | protobuf `IdentityLifecycleEvent` | `smartfarm/{tenant}/_global/domain/{event}/v1` | 🟢 (EVT-01/02 fixed) |

## Consumers
| Consumer | Subscribe | Mục đích | Status |
|----------|-----------|---------|:------:|
| order `OrderChangedConsumer` | order-changed | CQRS read-model (FILE persist + manual ACK) | 🟢 |
| inventory `TaskMqttSubscriber` | `task-changed/v1` only | react task | 🟡 hẹp |
| gateway `MqttEventSubscriber` → `DomainEventBus` → WS | `smartfarm/+/+/domain/#` | push browser qua `/ws` | 🟢 (protobuf; identity events giờ cũng tới được sau EVT-01) |

## Delivery semantics
- At-least-once qua outbox (order/livestock/identity/health).
- Idempotency: order event idempotency (migration V11); identity inbox idempotency (ISSUE-17 INFO).

## MQTT message integrity (HMAC)
Mọi publisher ký + consumer verify envelope `SFM1` HMAC-SHA256 (`libs/smartfarm-security`): dev bật sẵn, prod rollout 2 pha (default OFF). Live-verified 2026-10-07 (identity login.failed frame + 5/5 negative controls). Broker-level ACL+mTLS vẫn là sample config, chưa enable. Xem [`unresolved-items.md`](../audit/unresolved-items.md) (ISSUE-02).

## Known issues (còn OPEN)
- `smartfarm.events.mqtt.enabled` default `false` ở gateway (operator bật).
- `identity.command.result` = command-ack hướng WS, feature-flag OFF (ISSUE-06 by-design, không phải bug).
- ISSUE-10 (publisher cleanSession/client-id), ISSUE-13 (consumer timeout/circuit-breaker) — xem register.

← [Event flow diagram](../architecture/request-flows.md#6-mqtt-event-driven-general-event-communication)
