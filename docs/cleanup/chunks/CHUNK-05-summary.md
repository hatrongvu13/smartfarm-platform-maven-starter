# CHUNK-05 — services/smartfarm-inventory

**Scope:** inventory items/lots/movements/balances/reservations + MQTT task inbox.

**Graph manifest (graphslice --summary):**
- 168 nodes · inbound 0 / outbound 211
- annotations: {Entity:18, Repository:15, Service:5, GrpcService:3, Configuration:2}

**5-line summary**
1. Entry points: 3 gRPC services; MQTT TaskMqttSubscriber (@ConditionalOnProperty smartfarm.inventory.mqtt.enabled — OFF by default, security-verified, FILE persistence under ./.local/mqtt-inventory); InboxDevController (@Profile dev&!prod, diagnostics count only).
2. Persistence: 2 Flyway migrations (V1 baseline, V2 reorder_threshold); entities Item/Lot/Movement/Balance/Reservation/Inbox; InventoryRepository.
3. Idempotency: InboxEntity inbox-dedupe pattern present.
4. Tests: NONE under src/test (gap vs order/identity).
5. Verdict: PARTIAL_IMPLEMENTATION — real domain, persistence, gRPC and durable MQTT inbox, but no JUnit coverage and MQTT subscriber is opt-in (disabled by default). RISK: verification coverage.
