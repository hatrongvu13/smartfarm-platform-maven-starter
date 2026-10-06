# Service: Health (`services/smartfarm-health-service`)

> Verified @ HEAD `eaaa112`. Status: **PARTIAL + ORPHAN**. ← [System Overview](../architecture/system-overview.md)

## Trách nhiệm
Animal health: observation, examination, vaccination, treatment, alert.

## Inbound — gRPC `AnimalHealthService` (4/10 impl)
🟢 Impl: RecordObservation, ListObservations, RecordVaccination, ListVaccinations.
🔴 Chưa impl: ScheduleExamination, CompleteExamination, RecordTreatment, ListTreatments, ListAlerts, AcknowledgeAlert.

## Gateway reach
🔴 **HOÀN TOÀN ORPHAN**: không REST, không GraphQL, **không khai báo gRPC client** trong gateway `application.yml`. Không thể gọi health từ edge (GW-01).

## Event publish
`PahoHealthEventPublisher` + `HealthOutboxRelay` = `@Profile("dev & !prod")` và **từ chối broker non-loopback** → **không có đường event health ở prod** (EVT-03).

## Database
`V1__health_baseline.sql` (1 migration).

## Known limitations
- 6/10 RPC chưa impl.
- Zero gateway reach.
- Không có prod event path.

## TODO
- [ ] Impl 6 RPC còn thiếu.
- [ ] Khai báo gRPC client + route gateway cho health.
- [ ] Prod-capable event publisher.
