# 07 — Runtime Readiness

> Spec §19/§20/§30. Trả lời: clone repo rồi `build → start → connect → health → core flow → test` được không?
> Nhãn: FACT / INFERENCE / UNKNOWN / RISK / BLOCKER.

## Verdict
```
RUNTIME STATUS (prod):  RUNNABLE (conditional) — ISSUE-01 ĐÃ VÁ (order prod/base YAML dạng nested jwt.*)
RUNTIME STATUS (test):  RUNNABLE — CFG-01 resolved; all service test profiles use nested jwt.*
Infra:                  compose.yaml (Postgres17/Redis/Mosquitto) + deploy/kubernetes + observability stack
```

## Minimum Viable Runtime (spec §20)
```
Minimum services required:   identity-service (issuer JWKS) + ít nhất 1 service nghiệp vụ + gateway
Minimum dependencies:        Postgres 17, Mosquitto (MQTT), Redis (gateway cache/session)
Required environment:        SMARTFARM_JWT_ISSUER, SMARTFARM_JWKS_URI, DB creds, MQTT creds (prod)
Required database:           Postgres per-service schema; Flyway migrate (prod ddl-auto=validate)
Required configuration:      smartfarm.security.jwt.{issuer,jwk-set-uri,audiences} NESTED (bắt buộc)
Required startup order:      Postgres/Mosquitto/Redis → identity (JWKS) → services → gateway
Required ports:              gRPC per-service, gateway HTTP/WS, 1883 MQTT, 5432 PG, 6379 Redis
Required test:               mvn test (unit) + ITs gated by -Dit.postgres.enabled=true
Known blockers:              none for Order build/test; broker ACL/TLS remains a production infrastructure gate
```

## Checklist (spec §30)
```
[x] Build          — Maven reactor compile (ref docs/cleanup/11-verification-result.md: reactor compiles + tests pass)
[x] Dependencies   — resolve qua reactor; proto generated-sources build
[x] Configuration  — prod/dev/test use nested `smartfarm.security.jwt.*`; CFG-01 resolved
[x] Startup (prod) — ISSUE-01 vá; nested jwt.* có mặt ở order base+prod
[x] Database       — compose Postgres17 + Flyway V* per service
[x] Infrastructure — compose.yaml (PG/Redis/Mosquitto), k8s manifests, observability
[x] Health check   — readiness-service + actuator health per service
[x] Core API       — gRPC services + JwtServerInterceptor fail-closed
[x] Core flow      — order saga forward+compensation (worker path) COMPLETE
[x] Error handling — dispatch classifier + exponential backoff (libs/messaging)
[x] Minimal tests  — unit + ITs (inventory/identity/finance Testcontainers)
[x] Reproducible   — prod/dev/test configuration shape is consistent
```

## Build / Start / Connect / Health / Core flow
- **Build** (FACT): reactor compiles; proto generates stubs (`FarmOrderServiceGrpc`, `FarmDirectoryServiceGrpc` present).
- **Start** (FACT→INFERENCE): prod boots — order base/prod YAML dùng nested `jwt.*` (ISSUE-01 vá). Test profiles use isolated datasource configuration and nested JWT properties.
- **Connect** (INFERENCE): compose cung cấp PG/Redis/Mosquitto; mỗi service có datasource + MQTT client factory.
- **Health** (FACT): actuator + readiness-service (SSRF-safe).
- **Core flow** (FACT): order saga worker path COMPLETE (persistent saga, 11 Flyway, outbox at-least-once,
  CQRS ordered projection + gap recovery). Ref `docs/audit/03-business-workflow-report.md`.

## RISK carry-forward
- **RISK-DEV-01**: `dev` profile không được bật ở prod (gates dev facades + dev super-admin bootstrap).
- **RISK-BROKER-01**: prod MQTT cần `allow_anonymous false` + ACL + TLS (`docs/cleanup/10-mqtt-production-readiness.md`).
- **RISK-MQTT-SEC**: HMAC envelope is wired and live-verified; broker authentication, ACL and TLS remain open.
