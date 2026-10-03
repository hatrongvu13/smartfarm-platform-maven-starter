# CHUNK-06 — services/smartfarm-livestock

**Scope:** animal registry + task lifecycle with deadline SLAs + outbox.

**Graph manifest (graphslice --summary):**
- 216 nodes · inbound 0 / outbound 283
- annotations: {Service:14, Entity:12, Repository:11, GrpcService:3, Configuration:2}

**5-line summary**
1. Entry points: 3 gRPC services; scheduled ScheduleTaskGenerator + TaskDeadlineMonitor + OutboxRelay; MQTT MqttPublisher.
2. Persistence: 1 Flyway migration (V1 livestock_baseline); entities Animal/Task/Schedule/Outbox; TaskStore + OutboxRepository.
3. Lifecycle: full task lifecycle (create/assign/accept/complete/cancel) with accept/report deadline windows and TaskDeadlineMonitor enforcement.
4. Tests: no src/test JUnit; two integration shell harnesses (scripts/livestock-lifecycle-test.sh, scripts/livestock-registry-test.sh).
5. Verdict: PRODUCTION_IMPLEMENTATION — real scheduled task domain with deadline SLAs, outbox, gRPC contract. RISK: unit coverage is shell-based, not JUnit.
