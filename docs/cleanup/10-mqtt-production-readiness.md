# 10 — MQTT Production Readiness Marking (Phase 9)

> Marks what a REAL deployment needs for MQTT, without assuming any of it is enabled in
> dev. Grounded in CHUNK-12 (topic/producer/consumer map), CHUNK-13 (broker config), and
> a direct read of the actual `@ConfigurationProperties` prefixes (see §4 — the backlog's
> proposed `smartfarm.messaging.mqtt.*` namespace does NOT exist; keys are per-service).

## 1. Broker / network (currently dev-open — mark for prod)

| Item | Current (FACT) | Prod requirement |
|---|---|---|
| `allow_anonymous` | `true` in `infra/mosquitto.conf` | **`false`** for staging/prod; per-service credentials. |
| websockets listener `9001` | open, unauthenticated | internal-only / behind gateway; not public. |
| Topic ACL | none enabled (documented sample only) | ACL per publish/subscribe owner (per-service identity). |
| Broker exposure | dev compose maps broker to host | internal network only; no direct public exposure. |
| TLS / mTLS | not enabled | mark **optional/required per deployment decision** — do NOT assume enabled. |

**RISK-BROKER-01** remains: the app-layer HMAC envelope (ISSUE-01/02) protects message
*integrity/authenticity* but NOT broker-level *access control*. Both are needed in prod.

## 2. Message integrity (KEEP — already strong)

- Signed HMAC-SHA256 envelope (`libs/smartfarm-security` `MqttSecurityVerifier`), topic-bound,
  constant-time compare, toggles default-off. **Keep and document.** (ISSUE-01/02)
- Keys/secrets via env/secret store with a rotation plan (prod); never committed. (mark)
- Topic, tenant, producer and signature verified by every production consumer. (FACT)

## 3. Reliability (KEEP — already implemented)

- Producers use transactional **outbox** (order, identity, livestock, health). (FACT)
- Consumers use **inbox/dedup** (order projection inbox, identity command inbox, inventory
  task inbox). (FACT)
- Poison event → DEAD/continue; broker failure → retry/break — via
  `libs/smartfarm-messaging` `DispatchFailureClassifier` (ISSUE-04/05/09). (FACT)
- Durable consumer `OrderChangedConsumer`: stable client ID, FILE persistence, manual ACK
  after projector commit. (FACT)
- Gateway WS/realtime consumer is ephemeral and re-queries source after reconnect. (FACT)

## 4. Typed properties — RECONCILED against real bound keys

The backlog Phase 9 proposed a single `smartfarm.messaging.mqtt.*` namespace. **That
namespace is not bound anywhere** (FACT — no `@ConfigurationProperties(prefix=
"smartfarm.messaging.mqtt")` exists). The real, bound prefixes are per-service:

| Real prefix (FACT) | Bound by | Covers |
|---|---|---|
| `smartfarm.identity.mqtt` | `IdentityMqttProperties` | enabled, broker-uri, client-id, username, password, qos |
| `smartfarm.identity.mqtt.commands` | `IdentityMqttCommandProperties` | enabled, topic-filter, qos, max-payload, retention, cleanup/dispatch intervals, batch, retries, timeout |
| `smartfarm.identity.outbox` | `IdentityOutboxProperties` | identity outbox relay tuning |
| `smartfarm.security.mqtt` | `MqttSecurityProperties` | HMAC envelope toggles/keys (ISSUE-01/02) |
| `smartfarm.inventory.mqtt` | inventory subscriber | enabled, persistence-path |
| `MqttConsumerSettings` | plain value object (per-service bind) | cleanSession/persistence-mode guard (rejects memory+cleanSession=false hybrid) |

**Recommendation (DOCUMENT, not an edit this pass):** either (a) accept the per-service
namespaces as the real contract and document them, or (b) a future consolidation to a
shared `smartfarm.messaging.mqtt.*` is a deliberate refactor with migration — NOT a
cleanup rename, and must not be done by inventing keys. The audit's position is (a):
**document the real keys**; do not create the backlog's unbound namespace.

## 5. Production profile RISK

**RISK-DEV-01**: the `dev` Spring profile exposes dev-only MQTT surfaces (simulator
observer — now quarantined `dev & !prod`, inventory `InboxDevController`) and the dev
super-admin bootstrap. Verify the prod deploy never activates `dev`. (feeds `11`/`12`)

## Final MQTT readiness verdict
Message integrity + reliability are **production-grade already**. The gaps are
**deployment-level**: broker access control (anonymous/ACL/TLS), secret rotation, and the
per-service property namespaces to document. None require code changes in this cleanup.
