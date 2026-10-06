# Service: Reporting (`services/smartfarm-reporting-service`)

> Verified @ HEAD `eaaa112`. gRPC :9096. Status: **PARTIAL**. ← [System Overview](../architecture/system-overview.md)

## Trách nhiệm
Sinh report (render + download). Livestock report type hoạt động; non-livestock type chưa đủ.

## Inbound — gRPC `ReportingService`
(RPC definitions trong `reporting.proto`.) Server impl: `ReportingGrpcService`.

## Inbound — REST (gateway, dev-only)
`/api/v1/reports` (POST), `/{id}` (GET), `/` (GET list), `/{id}/download` (GET) — `ReportingDevController`, `@Profile dev&!prod`.

## Database
`V1__reporting_baseline.sql` (1 migration).

## Known limitations
- Non-livestock report types: read-models + object-storage resolver chưa đủ (PARTIAL ~40%).
- REST dev-only.

## TODO
- [ ] Hoàn thiện read-model cho non-livestock report types.
- [ ] Object-storage resolver cho download.
