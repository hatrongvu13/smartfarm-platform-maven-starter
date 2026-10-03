# CHUNK-03 — services/smartfarm-order

**Scope:** order aggregate, persistent saga, outbox, CQRS read model + recovery.

**Graph manifest (graphslice --summary):**
- 960 nodes · inbound 0 / outbound 842
- annotations: {Service:47, Entity:38, Repository:22, Configuration:10, GrpcService:6, Scheduled:1}

**5-line summary**
1. Entry points: gRPC (6); REST admin (saga admin, outbox admin, projection recovery); scheduled OrderSagaPersistentWorker + OrderProjectionGapRecoveryWorker + OrderOutboxRelay; MQTT OrderChangedConsumer + OrderMqttPublisher.
2. Persistence: 11 Flyway migrations V1..V11 (baseline → saga persistence → audit/versions → recovery audit → outbox snapshot → projection ordering → gap recovery → saga deadlines → admin hardening → granular events → entry-event idempotency). Entities for order/line, saga/step, outbox, projection inbox/view, recovery audit/archive/gap.
3. Saga: dead OrderSagaOrchestrator REMOVED from src/main (ISSUE-03); live path is claim-based OrderSagaPersistentWorker with forward+compensation executors, checkpoints, stale-claim recovery, manual-review deadlines.
4. Reliability: durable OrderChangedConsumer (cleanSession=false, FILE persistence, manual ACK, version-guard replay-safe) + gap-recovery backstop; OrderOutboxRelay poison handling via shared classifier; 8 tests.
5. Verdict: PRODUCTION_IMPLEMENTATION — most hardened service. OrderSagaOrchestrator remains only in docs/stale graph, not source.
