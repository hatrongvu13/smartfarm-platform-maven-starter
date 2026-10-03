# CHUNK-12 — MQTT / event-flow

**Scope:** every MQTT producer/consumer, the topics they use, and outbox/inbox tables.

**Topic convention:** `smartfarm/<tenant>/<farm>/domain/<event>/v1`. Production
publishers sign with the ISSUE-01/02 HMAC envelope (`mqttSecurity.sign`); production
consumers verify (`MqttSecurityVerifier.verify`) and drop/quarantine on failure.
All publishers QoS 1, lazy-connect. Single Paho construction point:
`libs/smartfarm-messaging MqttClientFactory`.

**Producers (publishers):**
- order `OrderMqttPublisher` ← `OrderOutboxRelay`: `.../domain/order-changed/v1` and `.../domain/order/<type>/v1`.
- livestock `MqttPublisher` ← `OutboxRelay`: `.../domain/<task-event>/v1` (e.g. task-assigned/v1).
- health `PahoHealthEventPublisher` ← `HealthOutboxRelay`: `.../domain/observation-recorded/v1` (loopback-only dev publisher).
- identity `IdentityMqttCommandDispatcher.publishResult` ← `IdentityEventRecorder`: command-result on the identity command topic family.

**Consumers (subscribers):**
- order `OrderChangedConsumer`: `smartfarm/+/+/domain/order-changed/+` — DURABLE (cleanSession=false, FILE persistence, manual ACK, replay-safe, envelope-verified).
- inventory `TaskMqttSubscriber`: `smartfarm/+/+/domain/task-changed/v1` — envelope-verified, inbox-dedupe, ACK withheld on DB failure, opt-in via @ConditionalOnProperty.
- identity `IdentityMqttCommandIntake`: `smartfarm/+/identity/command/+/v1` — durable inbox, dedupe, envelope+tenant/type/version validation.
- gateway `MqttEventSubscriber`: `smartfarm/+/+/domain/+/+` — fan-out bridge to in-process DomainEventBus → WebSocket; envelope-verified.
- simulator `TaskEventObserver`: `smartfarm/+/+/domain/task-changed/v1` — DIAGNOSTIC log-only, NO envelope verify (dev).

**Outbox / inbox tables:**
- order: OrderOutboxEntity (+relay/admin/health/poison classifier); inbox = OrderProjectionInboxEntity (projection drain + gap recovery).
- identity: IdentityOutboxEntity (+relay/admin/health); inbox = IdentityMqttInboxEntity (command dedupe + maintenance).
- livestock: OutboxEntity (publish-only, no inbox). health: HealthOutboxEntity (publish-only). inventory: InboxEntity (consume-only, no outbox).

**Verdict:** a coherent transactional-outbox + verified-envelope event mesh. order
and identity are the most complete (relay + inbox + poison handling via
smartfarm-messaging). Only the simulator consumer is unverified/diagnostic.
