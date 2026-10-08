# Service: Health (`services/smartfarm-health-service`)

> Verified @ HEAD (updated 2026-10-07). Status: **PARTIAL** (gRPC 4/10 impl; gateway + prod event path fixed). ← [System Overview](../architecture/system-overview.md)

## Trách nhiệm
Animal health: observation, examination, vaccination, treatment, alert.

## Inbound — gRPC `AnimalHealthService` (4/10 impl)
🟢 Impl: RecordObservation, ListObservations, RecordVaccination, ListVaccinations.
🔴 Chưa impl: ScheduleExamination, CompleteExamination, RecordTreatment, ListTreatments, ListAlerts, AcknowledgeAlert.
gRPC server port **:9097**.

## Gateway reach
🟡 **Dev REST facade (GW-01 fixed):** gateway khai báo gRPC client `AnimalHealthService` (`HealthDevClientConfig`, `smartfarm.health.grpc-host/grpc-port` → :9097) và lộ `HealthDevController` (`@Profile("dev & !prod")`) tại `/api/v1/health/{observations,vaccinations,alerts}` (scope `health:read`, per-service token audience `smartfarm-health`). Mirror `ReportingDevController`. **Prod exposure chưa có** — chỉ dev facade.

## Event publish
🟢 **Prod event path (EVT-03 fixed, Option A):** `HealthOutboxRelay`, `HealthOutboxScheduling`, `PahoHealthEventPublisher` không còn `@Profile("dev & !prod")` — active ở **mọi profile**, gate bằng `smartfarm.health.outbox.enabled` (default `true`). Publisher nhận broker cấu hình qua `smartfarm.health.mqtt.url` (chỉ chặn URL rỗng → prod misconfig fail-loud), ký HMAC qua `MqttSecurityVerifier` (nhất quán ISSUE-02). Payload protobuf `DomainEvent` (`health-*`).
> Lưu ý: trước fix, các key `smartfarm.health.outbox.*` + `mqtt.url` **chưa từng tồn tại** ở bất kỳ profile nào, nên bean relay/publisher inactive ở *cả dev lẫn prod*.

## Database
`V1__health_baseline.sql` (1 migration).

## Known limitations
- 6/10 RPC chưa impl.
- Gateway: chỉ dev REST facade, chưa có đường prod.

## TODO
- [ ] Impl 6 RPC còn thiếu.
- [ ] Prod REST/GraphQL route cho health (ngoài dev facade).
- [ ] Live-verify prod event path (cần health + gateway restart).
