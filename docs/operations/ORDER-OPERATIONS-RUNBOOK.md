# Order operations runbook

## First response

1. Record incident time, environment, alert name, and correlation ID if available.
2. Check `/actuator/health/readiness`, `/actuator/health`, and `/actuator/prometheus` on Gateway and Order.
3. Do not delete Saga, outbox, projection, or audit rows manually.
4. Do not force-complete or force-cancel without confirming downstream Inventory and Finance state.

## Gateway down

- Confirm the Gateway process and `/actuator/health/readiness`.
- Check JWT/JWKS configuration, service-token secret, TLS/DNS, and Order channel connectivity.
- Restore configuration or restart only after preserving logs.
- Recovery: `up{job="smartfarm-gateway"} == 1` for 10 minutes.

## Order service or database down

- Check `/actuator/health/readiness`; `db=DOWN` indicates database connectivity or pool failure.
- Verify database endpoint, credentials, Flyway state, pool saturation, and disk capacity.
- Do not bypass Flyway validation or switch to `ddl-auto=update` in production.
- Recovery: readiness `UP`, target `up=1`, and workers reduce existing backlog.

## Saga backlog or stale claim

- Inspect `smartfarm_order_saga_active`, `smartfarm_order_saga_oldest_active_seconds`, and `smartfarm_order_saga_stale_claims`.
- Confirm that the persistent worker is enabled and that Inventory/Finance gRPC endpoints are reachable.
- For stale claims, use the authenticated internal recovery operation `POST /internal/order-sagas/recover-stale` with tenant and reason.
- Recovery: stale claims return to zero and oldest active age decreases.

## Saga manual review

- Use Gateway `GET /api/v1/order-sagas/{id}` with `orders:saga:admin`.
- Reconcile Inventory reservations/commits and Finance expense/reversal before choosing retry, resume, force-compensate, force-cancel, force-complete, or mark-resolved.
- Prefer retry/resume. Force actions require an incident record and operator reason.
- Recovery: Saga reaches `COMPLETED` or `COMPENSATED`; manual-review gauge returns to zero.

## Outbox backlog or MQTT unavailable

- Inspect pending count, oldest pending age, stale claims, broker reachability, credentials, and topic authorization.
- The publisher connects lazily. Do not make readiness depend on MQTT.
- Restore broker connectivity and allow normal retries. Stale publishing claims are automatically recovered by maintenance.
- Recovery: oldest pending age and pending count decrease continuously.

## Outbox dead events

- List internal dead events with `GET /internal/order-outbox?status=DEAD` using `orders:outbox:admin` and tenant header.
- Fix the broker, credentials, invalid topic segment, or payload cause before retrying.
- Retry one event at a time with `POST /internal/order-outbox/{eventId}/retry`.
- Never update status or attempt count directly in the database.

## Projection gap backlog

- Inspect `smartfarm_order_projection_gaps_pending` and oldest-gap seconds.
- Verify MQTT delivery, projection worker, event archive availability, and database health.
- Allow automatic archive replay while the expected archived event exists.
- Recovery: pending gap count and age decrease; read model version advances.

## Projection manual review or archive missing

- Inspect `GET /internal/order-projections/gaps/{gapId}` with `orders:admin`.
- Confirm that the next missing aggregate version exists in `ord_order_event_archive`.
- If present, replay only the expected next version through the authenticated replay endpoint.
- If absent, escalate. Do not fabricate an event or skip aggregate versions.

## Escalation

Escalate immediately for database data loss, partial Inventory commit, Finance reconciliation mismatch, missing immutable archive events, or repeated dead outbox events after the root cause is fixed.

## Deployment and rollback

- Deploy immutable image digests with rolling update, `maxUnavailable=0`, and `maxSurge=1`.
- Wait for both Gateway and Order rollouts, then verify readiness and backlog age metrics.
- Keep a 45-second termination grace period; do not force-delete a terminating Order pod.
- Roll back with `kubectl rollout undo deployment/smartfarm-order` or `smartfarm-gateway`.
- After rollback, verify stale claims return to zero and no outbox event entered `DEAD`.
