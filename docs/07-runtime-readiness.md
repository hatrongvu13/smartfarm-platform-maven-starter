# 07 — Runtime Readiness

> Spec §19/§20/§30. Trả lời: clone repo rồi `build → start → connect → health → core flow → test` được không?
> Nhãn: FACT / INFERENCE / UNKNOWN / RISK / BLOCKER.

## Verdict
```
RUNTIME STATUS (prod):  RUNNABLE (conditional) — ISSUE-01 ĐÃ VÁ (order prod/base YAML dạng nested jwt.*)
RUNTIME STATUS (test):  BLOCKED cho profile `test` — CFG-01 (6/7 application-test.yml dùng flat key)
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
Known blockers:              CFG-01 (test profile boot) — xem backlog/v0.1/V0.1-001
```

## Checklist (spec §30)
```
[x] Build          — Maven reactor compile (ref docs/cleanup/11-verification-result.md: reactor compiles + tests pass)
[x] Dependencies   — resolve qua reactor; proto generated-sources build
[~] Configuration  — prod/dev OK; `test` profile BROKEN cho 6 service (CFG-01)
[x] Startup (prod) — ISSUE-01 vá; nested jwt.* có mặt ở order base+prod
[x] Database       — compose Postgres17 + Flyway V* per service
[x] Infrastructure — compose.yaml (PG/Redis/Mosquitto), k8s manifests, observability
[x] Health check   — readiness-service + actuator health per service
[x] Core API       — gRPC services + JwtServerInterceptor fail-closed
[x] Core flow      — order saga forward+compensation (worker path) COMPLETE
[x] Error handling — dispatch classifier + exponential backoff (libs/messaging)
[x] Minimal tests  — unit + ITs (inventory/identity/finance Testcontainers)
[~] Reproducible   — prod/dev reproducible; `test` profile not reproducible until CFG-01 fixed
```

## Build / Start / Connect / Health / Core flow
- **Build** (FACT): reactor compiles; proto generates stubs (`FarmOrderServiceGrpc`, `FarmDirectoryServiceGrpc` present).
- **Start** (FACT→INFERENCE): prod boots — order base/prod YAML dùng nested `jwt.*` (ISSUE-01 vá). Chạy với
  `SPRING_PROFILES_ACTIVE=test` sẽ FAIL ở 6 service do `SmartFarmSecurityProperties` ném `smartfarm.security.jwt is required`.
- **Connect** (INFERENCE): compose cung cấp PG/Redis/Mosquitto; mỗi service có datasource + MQTT client factory.
- **Health** (FACT): actuator + readiness-service (SSRF-safe).
- **Core flow** (FACT): order saga worker path COMPLETE (persistent saga, 11 Flyway, outbox at-least-once,
  CQRS ordered projection + gap recovery). Ref `docs/audit/03-business-workflow-report.md`.

## RISK carry-forward
- **RISK-DEV-01**: `dev` profile không được bật ở prod (gates dev facades + dev super-admin bootstrap).
- **RISK-BROKER-01**: prod MQTT cần `allow_anonymous false` + ACL + TLS (`docs/cleanup/10-mqtt-production-readiness.md`).
- **RISK-MQTT-SEC**: HMAC envelope (`MqttSecurityVerifier`) đã hiện thực nhưng mặc định TẮT; chưa nối vào publisher/consumer thực (ISSUE-02 còn OPEN phần wiring).
