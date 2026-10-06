# Events — SmartFarm Platform (MQTT)

> Verified từ outbox/publisher/consumer @ HEAD `eaaa112`. Broker: Mosquitto :1883.

## Topic schema (chuẩn)
`smartfarm/{tenantId}/{farmId}/domain/{aggregate}-{event}/v1` — payload = protobuf `DomainEvent`.

## Publishers (outbox relay → MQTT)
| Service | Payload | Topic | Status |
|---------|---------|-------|:------:|
| order | protobuf | `.../domain/order-changed/v1` | 🟢 |
| livestock | protobuf | `.../domain/task-changed/v1` + lifecycle leaves | 🟢 |
| health | protobuf | `.../domain/health-*/v1` | 🟡 dev-only, loopback-only |
| identity | **JSON** | `smartfarm/{tenant}/_global/domain/{aggr}-{event}/v{n}` | 🔴 mismatch (EVT-01/02) |

## Consumers
| Consumer | Subscribe | Mục đích | Status |
|----------|-----------|---------|:------:|
| order `OrderChangedConsumer` | order-changed | CQRS read-model (FILE persist + manual ACK) | 🟢 |
| inventory `TaskMqttSubscriber` | `task-changed/v1` only | react task | 🟡 hẹp |
| gateway `MqttEventSubscriber` → `DomainEventBus` → WS | `smartfarm/+/+/domain/#` | push browser qua `/ws` | 🟢 (nhưng expect protobuf → identity JSON rớt) |

## Delivery semantics
- At-least-once qua outbox (order/livestock/identity/health).
- Idempotency: order event idempotency (migration V11); identity inbox idempotency (ISSUE-17 INFO).

## Known issues
- EVT-01 identity JSON ≠ protobuf → WS bridge parse fail (HIGH).
- EVT-02 identity topic schema khác chuẩn.
- EVT-03 health no prod event path.
- `identity.command.result` orphan (ISSUE-06).
- Config flag: `smartfarm.events.mqtt.enabled` default `false` ở gateway.

← [Event flow diagram](../architecture/request-flows.md#6-mqtt-event-driven-general-event-communication)
