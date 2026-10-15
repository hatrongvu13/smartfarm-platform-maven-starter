# SmartFarm Platform — Maven monorepo

Một repository Git duy nhất, Maven **monorepo** đa module. Root `pom.xml` vừa là **parent POM** vừa là **reactor aggregator** (**14 module** con). Source `com.htv.smartfarm`, **Spring Boot 4.1.1**, **Spring gRPC 1.1.1**, **Java 17**.

> 📖 **Toàn bộ tài liệu điều hướng từ [`docs/index.md`](./docs/index.md).**

## 1. Project overview & mục tiêu

Nền tảng quản lý nông trại chăn nuôi theo kiến trúc **microservice**: một **gateway** (edge, REST + GraphQL + WebSocket) dịch sang **gRPC** gọi các service nghiệp vụ; sự kiện qua **MQTT** (outbox/inbox); cơ sở dữ liệu và migration được cấu hình theo từng module/profile. Mục tiêu: nền tảng chăn nuôi end-to-end (identity/RBAC, order-saga, inventory, finance, livestock, health, reporting) tiến tới production, mở rộng sang warehouse (V2) và IoT/connected-farm (V3).

## 2. Current status

- The source tree contains 14 Maven reactor modules. This is a development platform with working service and gateway code, not a production-readiness assertion.
- Runtime capabilities and limitations are described in docs linked below; all status claims must be checked against source/configuration and tests.
- Identity has a token grant facade, relational refresh-session persistence, and session gRPC handlers. Service-token HTTP issuance now requires a separate shared internal header secret and gRPC issue/exchange require a caller JWT, but issuance still has duplicate transports and gRPC TLS is off by default. Bootstrap no longer generates/logs an enrollment URI; authenticated post-bootstrap enrollment remains a distinct flow.
- Other known gaps include the unimplemented farm directory RPCs and six health RPCs, development-only gateway facades, and production MQTT broker hardening.

## 3. Danh sách service (14 module reactor)

| Nhóm | Module |
|------|--------|
| Edge | `apps/smartfarm-gateway` |
| Services | identity, order, inventory, finance, livestock, health, reporting (`services/smartfarm-*-service`) |
| Platform | `platform/smartfarm-readiness-service`, `platform/smartfarm-farm-simulator` |
| Libs | `libs/smartfarm-{common-kernel,security,proto,messaging}` |

Chi tiết từng service: [`docs/services/`](./docs/services/gateway.md).

## 4. Quick start

```bash
# 1. Yêu cầu: JDK 17+, Maven 3.9+, Git, Docker Compose, Python 3
./scripts/doctor.sh          # verify toolchain + 14 module reactor
./scripts/bootstrap.sh       # mvn -B clean install
docker compose up -d         # postgres:17, mosquitto:2, redis:8

# 2. Khởi chạy dev (profile dev tự nhận, KHÔNG cần set SPRING_PROFILES_ACTIVE)
mvn -f services/smartfarm-identity-service/pom.xml  spring-boot:run   # :8092
mvn -f services/smartfarm-livestock-service/pom.xml spring-boot:run   # :8081 / gRPC 9091
mvn -f apps/smartfarm-gateway/pom.xml               spring-boot:run   # :8080 INGRESS

# 3. Smoke test + health
./scripts/run-all.sh        # health and smoke checks; requires a live gateway
curl http://localhost:8080/actuator/health
```

Hướng dẫn đầy đủ: [`docs/operations/local-development.md`](./docs/operations/local-development.md).

### Port (profile dev)
Ingress công khai **chỉ** gateway `:8080`; service nghiệp vụ là gRPC nội bộ.

| Module | HTTP | gRPC |
|--------|-----:|-----:|
| gateway | 8080 | — |
| identity | 8092 | 9092 |
| livestock | 8081 | 9091 |
| inventory | 8083 | 9093 |
| finance | 8084 | 9094 |
| order | 8085 | 9095 |
| reporting | 8086 | 9096 |
| health | 8087 | 9097 |
| readiness | 8090 | 9100 |
| farm-simulator | 8091 | 9101 |
| Hạ tầng | Postgres 5432 · Mosquitto 1883/9001 · Redis 6379 | |

## 5. Architecture overview
gateway (edge) → gRPC → services; MQTT event bus (outbox/inbox); database and cache integrations vary by module/profile. Sơ đồ + chi tiết: [`docs/architecture/system-overview.md`](./docs/architecture/system-overview.md).

## 6. Gateway mapping overview
Client → gateway REST/GraphQL → gRPC service đích. Bảng route đầy đủ + gap: [`docs/architecture/gateway-mapping.md`](./docs/architecture/gateway-mapping.md).

## 7. Service communication overview
REST (client↔gateway, gateway↔identity auth), gRPC (gateway↔services, order↔inventory/finance saga), MQTT event. Ma trận: [`docs/architecture/service-communications.md`](./docs/architecture/service-communications.md). Luồng request: [`docs/architecture/request-flows.md`](./docs/architecture/request-flows.md).

## 8. Documentation index
→ [`docs/index.md`](./docs/index.md) (kiến trúc · API · services · operations · security).

## 9. Security
Identity implements RS256/JWKS tokens, relational refresh rotation/reuse detection, TOTP MFA, and DB-backed RBAC. Open risks include duplicate service-token transports, disabled-by-default gRPC TLS, and broker ACL/TLS configuration. MQTT HMAC is enabled in dev and defaults off in prod. Details: [`SECURITY.md`](./SECURITY.md), [`docs/security/security-architecture.md`](./docs/security/security-architecture.md).

## 10. License
Source-available dưới **PolyForm Strict License 1.0.0** — xem [`LICENSE`](./LICENSE).
Copyright © 2026 Vu Ha Trong — xem [`COPYRIGHT`](./COPYRIGHT).
Chỉ được **xem, nghiên cứu, và chạy** cho mục đích **phi thương mại** được phép. Thương mại hoá, phân phối lại, chỉnh sửa, sublicense, và tạo sản phẩm phái sinh đều **không** được phép trừ khi có uỷ quyền riêng bằng văn bản từ chủ sở hữu bản quyền. Liên hệ cấp phép thương mại qua chủ repository trên GitHub.

## 11. Known limitations
- Identity service-token issuance has two transports; HTTP requires `IDENTITY_INTERNAL_API_SECRET` plus client credentials, while gRPC issue/exchange require an authenticated caller JWT plus client credentials. gRPC TLS defaults off; choose and harden a single transport.
- The dev profile declares a Gateway service-token client, but the latest local Identity startup reported `clientCount=0`; issuance with the correct internal header returned 401 (`bad_credentials`). Successful local token issuance remains unverified.
- Identity bootstrap no longer generates or logs TOTP enrollment secrets; authenticated enrollment is a separate MFA challenge flow.
- The identity token/session RPC adapter, JPA refresh-session persistence, and session RPC methods exist, but focused server-registration/security and concurrent PostgreSQL coverage remains incomplete.
- gRPC inventory `AdjustStock` and `TraceLot` have implementations (older docs claiming they are missing are stale); FarmDirectory has five unimplemented RPCs and AnimalHealthService has six.
- Some gateway service facades are development-profile-only. MQTT production broker ACL/TLS remains an operational gap; app-level HMAC defaults off in production.
See [`docs/api/grpc-api.md`](./docs/api/grpc-api.md), [`docs/services/identity.md`](./docs/services/identity.md), and [`docs/security/security-architecture.md`](./docs/security/security-architecture.md).

## 12. Roadmap (tóm tắt)
**V0 Foundation ✅** → **V1 Core Platform (đang triển khai)** → **V2 Warehouse** → **V3 Connected Farm**. Chi tiết + milestone map: [`docs/roadmap.md`](./docs/roadmap.md). Identity backlog: [`IDENTITY_BACKLOG_UPDATED.md`](./IDENTITY_BACKLOG_UPDATED.md).

## 13. TODO checklist còn hiệu lực
- [ ] Secure the chosen service-token transport and remove/protect duplicate issuance routes.
- [ ] Add concurrent bootstrap PostgreSQL test; bootstrap TOTP enrollment secrets are no longer generated or logged.
- [ ] Add server-level gRPC registration/policy coverage for IdentityTokenService and integration proof for replay/revoke behavior.
- [ ] Implement the five FarmDirectory RPCs and six missing AnimalHealth RPCs, or explicitly retire their proto contracts.
- [ ] Enable production gRPC TLS and broker authentication/ACL/TLS; keep HMAC rollout coordinated.
- [ ] Reconcile remaining identity permission ownership, mutation/version, paging, and proto-contract gaps: [`IDENTITY_BACKLOG_UPDATED.md`](./IDENTITY_BACKLOG_UPDATED.md).

## 14. Layout
```
pom.xml                 parent + aggregator (14 module)
libs/                   common-kernel · security · proto · messaging (không chạy riêng)
platform/               readiness-service · farm-simulator
services/               identity · livestock · health · inventory · finance · order · reporting
apps/smartfarm-gateway  edge REST/GraphQL/WS → gRPC
docs/                   index.md + architecture/api/services/operations/security
IDENTITY_BACKLOG_UPDATED.md  identity implementation checklist and next-phase plan
```
