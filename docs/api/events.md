# Events — SmartFarm Platform (MQTT)

> Reconciled 2026-10-16 from MQTT publisher/consumer source and profile configuration. Source implementation does not by itself prove broker delivery or production rollout.

## Topic and envelope

The common domain-event shape is `smartfarm/{tenantId}/{farmId}/domain/{event}/v1`; service-specific topics can have additional path segments, so consumers must use their documented/source-defined filter rather than assume one universal topic shape. Payload is protobuf `DomainEvent`; `EventMetadata` includes `event_id`, `tenant_id`, `farm_id`, producer, correlation/causation, and aggregate version fields.

Identity uses `smartfarm/{tenantId}/_global/domain/{event}/v1`: `_global` is the farm segment in this topic convention, not a global tenant or an authorization grant. Identity publishes `DomainEvent` with an `IdentityLifecycleEvent` or `AuthorizationInvalidated` payload. Authorization invalidations use the event topic `smartfarm/{tenantId}/_global/domain/authorization-invalidated/v1`.

## Publishers (outbox relay → MQTT)
| Service | Payload | Topic | Status |
|---------|---------|-------|:------:|
| order | protobuf | `.../domain/order-changed/v1` | 🟢 |
| livestock | protobuf | `.../domain/task-changed/v1` + lifecycle leaves | 🟢 |
| health | protobuf `DomainEvent` | `.../domain/health-*/v1` | 🟡 publisher/configuration exists; prod HMAC defaults off and broker delivery is deployment-dependent |
| identity | protobuf `DomainEvent` containing `IdentityLifecycleEvent` or `AuthorizationInvalidated` | `smartfarm/{tenant}/_global/domain/{event}/v1` | 🟢 source path; broker delivery is deployment-dependent |

## Consumers
| Consumer | Subscribe | Mục đích | Status |
|----------|-----------|---------|:------:|
| order `OrderChangedConsumer` | order-changed | CQRS read-model (FILE persist + manual ACK) | 🟢 |
| inventory `TaskMqttSubscriber` | `task-changed/v1` only | react task | 🟡 hẹp |
| gateway `MqttEventSubscriber` → `DomainEventBus` → `EventWebSocketHandler` | `smartfarm/+/+/domain/#` | authenticated JWT tenant filter and optional farm filter before `/ws/events` delivery | 🟢 source filters by tenant; this does not prove broker ACLs or runtime delivery |
| gateway authorization invalidation handler | broad `smartfarm/+/+/domain/#` subscription | `MqttEventSubscriber` diverts `AuthorizationInvalidated` to the watermark handler and does not forward it to WebSocket; the handler updates process-local watermarks | 🟡 eventual per-process observation; generic Gateway path does not compare topic, metadata, and payload tenant |
| Order/Reporting authorization invalidation consumer | `smartfarm/+/_global/domain/authorization-invalidated/v1` when enabled | security-library consumer verifies the envelope and validates topic/payload/metadata tenant before updating process-local watermarks | 🟡 enabled in base config, disabled in dev; still no shared authoritative version read |

## Delivery semantics
- At-least-once qua outbox (order/livestock/identity/health).
- Idempotency: order event idempotency (migration V11); Identity command inbox deduplicates by `command_id`. Do not infer every domain consumer has deduplication from those implementations.

## MQTT message integrity (HMAC)
`MqttSecurityVerifier` and the `SFM1` HMAC-SHA256 envelope are implemented in `libs/smartfarm-security`. Current service profile files configure signing/verification on in dev and off by default in prod for configured publishers/consumers. The application defaults alone do not prove every integration path is wired or that deployed brokers enforce signatures. Broker-level authentication, ACL, and TLS remain separate controls and are not enabled by the repository's Mosquitto config. See [`mqtt-acl-mtls.md`](../security/mqtt-acl-mtls.md).

## Known issues (còn OPEN)
- Gateway event subscription is configured by `smartfarm.events.mqtt.enabled` / `SMARTFARM_MQTT_EVENTS_ENABLED`, default `true` in `application.yml`; actual connection still depends on broker availability and profile configuration.
- Authorization invalidation uses a process-local watermark store by default. Gateway observes invalidations through its broad event subscriber; Order/Reporting use the dedicated security-library consumer outside dev. MQTT events advance a node's watermark when received; current source does not guarantee a maximum cross-node staleness or synchronously fetch the latest version on each request.
- Gateway uses event metadata tenant/farm for fan-out, falling back to topic segments only when metadata is blank. It does not currently reject a non-empty metadata/topic mismatch; validate this per event family before strengthening enforcement.
- `identity.command.result` = command-ack hướng WS, feature-flag OFF (ISSUE-06 by-design, không phải bug).
- ISSUE-10 (publisher cleanSession/client-id), ISSUE-13 (consumer timeout/circuit-breaker) — xem register.

← [Event flow diagram](../architecture/request-flows.md#6-mqtt-event-driven-general-event-communication)
