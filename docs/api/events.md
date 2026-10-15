# Events — SmartFarm Platform (MQTT)

> Reconciled 2026-10-15 from MQTT publisher/consumer source and profile configuration. Source implementation does not by itself prove broker delivery or production rollout.

## Topic schema (chuẩn)
`smartfarm/{tenantId}/{farmId}/domain/{aggregate}-{event}/v1` — payload = protobuf `DomainEvent`.

## Publishers (outbox relay → MQTT)
| Service | Payload | Topic | Status |
|---------|---------|-------|:------:|
| order | protobuf | `.../domain/order-changed/v1` | 🟢 |
| livestock | protobuf | `.../domain/task-changed/v1` + lifecycle leaves | 🟢 |
| health | protobuf `DomainEvent` | `.../domain/health-*/v1` | 🟡 publisher/configuration exists; prod HMAC defaults off and broker delivery is deployment-dependent |
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
`MqttSecurityVerifier` and the `SFM1` HMAC-SHA256 envelope are implemented in `libs/smartfarm-security`. Current service profile files configure signing/verification on in dev and off by default in prod for configured publishers/consumers. The application defaults alone do not prove every integration path is wired or that deployed brokers enforce signatures. Broker-level authentication, ACL, and TLS remain separate controls and are not enabled by the repository's Mosquitto config. See [`mqtt-acl-mtls.md`](../security/mqtt-acl-mtls.md).

## Known issues (còn OPEN)
- Gateway event subscription is configured by `smartfarm.events.mqtt.enabled` / `SMARTFARM_MQTT_EVENTS_ENABLED`, default `true` in `application.yml`; actual connection still depends on broker availability and profile configuration.
- `identity.command.result` = command-ack hướng WS, feature-flag OFF (ISSUE-06 by-design, không phải bug).
- ISSUE-10 (publisher cleanSession/client-id), ISSUE-13 (consumer timeout/circuit-breaker) — xem register.

← [Event flow diagram](../architecture/request-flows.md#6-mqtt-event-driven-general-event-communication)
