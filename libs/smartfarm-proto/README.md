# smartfarm-proto (Java 17)

Contract-first module for internal gRPC and MQTT binary payloads. Replace only `libs/smartfarm-proto` in the workspace; keep the Maven root aggregator and other repositories unchanged.

## Build

```bash
mvn -f libs/smartfarm-proto/pom.xml clean install
# Or from the root workspace:
mvn -pl libs/smartfarm-proto -am clean install
```

The existing `LivestockTaskService.CreateTask` and `Ping` RPCs and fields in their messages are preserved for the starter implementation. Newly declared RPCs are **contracts only**; their server implementations do not yet exist. `FarmOrderService`, `FarmDirectoryService`, `IdentityDirectoryService` define future owners, not running services. Do not register them until implemented.

## Directory map

- `common`: request context, paging, money, decimal quantity, date range, existing ping.
- `farm`: farm/barn directory contract (future owner).
- `livestock`: animal registry, scheduled/assigned/completed tasks.
- `health`: observation, examination, vaccination, treatment, alerts.
- `inventory`: item/lot, immutable stock movement, reservation, low-stock, traceability.
- `finance`: income/expense, debt, batch cost, cash flow.
- `order`: future order and saga owner (no service implementation yet).
- `reporting`: async export job and download location.
- `identity`: internal authorization directory (not a token issuer).
- `readiness`: platform-wide aggregate; standard gRPC Health for each server.
- `telemetry`: IoT and simulator MQTT payloads.
- `events`: typed MQTT integration event envelope.

## Contract rules

1. Never change/reuse existing field numbers or enum values. Reserve deleted tags and names; breaking changes use a new `v2` package.
2. IDs are strings (UUID/ULID is an implementation choice). Use UTC `google.protobuf.Timestamp` for instants. Use farm IANA timezone for recurrence/scheduling.
3. `Money.minor_units` is integer in currency minor units. `Quantity.decimal_value` is a base-10 string; validate units and precision at service boundaries.
4. All commands must carry an idempotency key via `RequestContext`; validate it at service boundary. Do not trust `actor_id` or `tenant_id` from request body without matching verified JWT/service identity.
5. `PageRequest.page_token` is opaque. Pagination ordering and max page size are implementation policy.
6. Do not send access tokens inside proto messages or MQTT payloads. Propagate auth via gRPC metadata; MQTT publisher uses broker credentials and ACLs.
7. `DomainEvent.metadata.event_id` is the dedup key; publish from transactional outbox, consume with inbox/idempotency. One event payload per envelope. MQTT QoS 1 implies possible redelivery; not exactly-once business processing.
8. Proto types are transport contracts, not shared JPA entities or canonical domain models. Keep each bounded context's persistence and validation private.
9. Standard `grpc.health.v1.Health` is the per-service readiness protocol; `PlatformReadinessService` aggregates dependency health without redefining the standard service.

## MQTT topic convention (planned)

- Domain: `smartfarm/{tenantId}/{farmId}/domain/{event-name}/v1` with serialized `smartfarm.events.v1.DomainEvent`.
- Device: `smartfarm/{tenantId}/{farmId}/devices/{deviceId}/telemetry/v1` with serialized `DeviceTelemetry`.
- Simulator: `smartfarm/{tenantId}/{farmId}/simulator/{simulatorId}/status/v1` with serialized `FarmStatusSimulation`.
- `event-name` examples: `task-changed`, `stock-reservation-changed`, `health-alert-raised`, `order-changed`, `export-job-changed`.
- Use QoS 1, `retain=false` for events; device status may use a separate retained topic after defining LWT policy. Broker must enforce tenant-scoped ACL and authentication outside local development.

## Example use in a consumer POM

```xml
<dependency>
  <groupId>com.htv.smartfarm</groupId>
  <artifactId>smartfarm-proto</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Caveats

This archive contains only the proto module. gRPC implementations, MQTT publishers/consumers, validation, auth and database migrations are separate work. Local validation checks import paths, declared types, tags and POM XML; a full `mvn clean install` requires Maven and dependency downloads on your machine.
