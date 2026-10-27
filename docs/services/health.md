# Service: Health (`services/health-service`)

> Reconciled 2026-10-15 from current source/profile configuration. Status: **PARTIAL** (gRPC 4/10 handlers; gateway facade is dev-only; event delivery is not claimed as live-verified). ← [System Overview](../architecture/system-overview.md)

## Trách nhiệm
Animal health: observation, examination, vaccination, treatment, alert.

## Inbound — gRPC `AnimalHealthService` (4/10 impl)
🟢 Impl: RecordObservation, ListObservations, RecordVaccination, ListVaccinations.
🔴 Chưa impl: ScheduleExamination, CompleteExamination, RecordTreatment, ListTreatments, ListAlerts, AcknowledgeAlert.
gRPC server port **:9097**.

## Gateway reach
🟡 **Dev REST facade (GW-01 fixed):** gateway khai báo gRPC client `AnimalHealthService` (`HealthDevClientConfig`, `farmverse.health.grpc-host/grpc-port` → :9097) và lộ `HealthDevController` (`@Profile("dev & !prod")`) tại `/api/v1/health/{observations,vaccinations,alerts}` (scope `health:read`, per-service token audience `farmverse-health`). Mirror `ReportingDevController`. **Prod exposure chưa có** — chỉ dev facade.

## Event publish
🟡 Publisher/outbox code is not restricted to the dev profile and its configuration includes an enabled-by-default outbox setting. The publisher emits protobuf `DomainEvent`; HMAC signing is profile-dependent (the prod profile defaults signing/verification off). Actual broker delivery and production rollout are not established by source configuration alone.

## Database
`V1__health_baseline.sql` (1 migration).

## Known limitations
- 6/10 RPC chưa impl.
- Gateway: chỉ dev REST facade, chưa có đường prod.

## TODO
- [ ] Impl 6 RPC còn thiếu.
- [ ] Prod REST/GraphQL route cho health (ngoài dev facade).
- [ ] Verify deployed production event delivery and HMAC rollout; do not infer these from bean availability.
