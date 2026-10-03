# Repository Manifest

```
Repo:        smartfarm-platform-maven-starter
Type:        Maven monorepo (reactor)
Stack:       Spring Boot 4.1.1 / Java 17, gRPC (spring-grpc), MQTT (Mosquitto/Paho),
             Postgres 17, Redis, Flyway
Scale:       MEDIUM  (15 Maven modules, ~422 Java src files under services+libs)
Branch:      main   HEAD 51f23a7
```

## Module inventory (15 modules)

| Module | Class | Role | Maturity (ref docs/cleanup/12) |
|---|---|---|---|
| `libs/smartfarm-proto` | GENERATED+CRITICAL | gRPC/proto contracts | PRODUCTION_READY |
| `libs/smartfarm-common-kernel` | CRITICAL | shared domain kernel | PRODUCTION_READY |
| `libs/smartfarm-security` | CRITICAL | JWT interceptor, GrpcMethodPolicy, MQTT HMAC envelope | PRODUCTION_READY |
| `libs/smartfarm-messaging` | CRITICAL | outbox/inbox infra, dispatch classifier, backoff | PRODUCTION_READY |
| `apps/smartfarm-gateway` | CRITICAL | edge tier, MQTT→WS event bus | PRODUCTION_READY |
| `services/smartfarm-identity-service` | CRITICAL | auth, RBAC, MFA/TOTP, token, MQTT command inbox | PRODUCTION_READY |
| `services/smartfarm-order-service` | CRITICAL | persistent saga, outbox, CQRS projection | PRODUCTION_READY* |
| `services/smartfarm-finance-service` | CRITICAL | ledger, idempotent saga compensation | PRODUCTION_READY |
| `services/smartfarm-inventory-service` | IMPORTANT | balance/reservation, gRPC, MQTT inbox | PRODUCTION_READY |
| `services/smartfarm-livestock-service` | IMPORTANT | task lifecycle + outbox | PRODUCTION_READY |
| `services/smartfarm-health-service` | IMPORTANT | observation/vaccination, Flyway-owned schema | PRODUCTION_READY |
| `services/smartfarm-reporting-service` | SUPPORTING | report render + download seam | PARTIAL_WITH_BACKLOG |
| `services/smartfarm-readiness-service` | SUPPORTING | SSRF-safe readiness probing | PRODUCTION_READY |
| `apps/smartfarm-web` | SUPPORTING | UI app | PARTIAL_WITH_BACKLOG |
| `(reactor root)` | — | aggregator pom | — |

`*` order-service PRODUCTION_READY cho đường worker; mang ISSUE-01 (prod config) + ISSUE-03/04/05 (tech-debt/reliability).

## Priority (entry-point first, spec §9)
`ENTRY POINT (gateway, *Application) → PUBLIC API (gRPC services) → PROTO (libs/proto) → CORE DOMAIN (common-kernel, service domain/) → SERVICE → DATABASE (Flyway V*) → INTEGRATION (MQTT outbox/inbox) → CONFIGURATION (application-*.yml) → TEST → DOCUMENTATION`

## Ignored (ghi nhận, không audit sâu)
`**/target/`, `**/generated-sources/`, IDE metadata, binary assets — trừ khi là source of truth.
`libs/smartfarm-proto/target/generated-sources/protobuf/**` là GENERATED từ `.proto` (source of truth = `.proto`).
