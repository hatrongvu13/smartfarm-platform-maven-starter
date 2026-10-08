# SmartFarm Platform — Maven monorepo

Một repository Git duy nhất, Maven **monorepo** đa module. Root `pom.xml` vừa là **parent POM** vừa là **reactor aggregator** (**14 module** con). Source `com.htv.smartfarm`, **Spring Boot 4.1.1**, **Spring gRPC 1.1.1**, **Java 17**.

> 📖 **Toàn bộ tài liệu điều hướng từ [`docs/index.md`](./docs/index.md).**

## 1. Project overview & mục tiêu

Nền tảng quản lý nông trại chăn nuôi theo kiến trúc **microservice**: một **gateway** (edge, REST + GraphQL + WebSocket) dịch sang **gRPC** gọi các service nghiệp vụ; sự kiện qua **MQTT** (outbox/inbox); mỗi service có Postgres (Flyway). Mục tiêu: nền tảng chăn nuôi end-to-end (identity/RBAC, order-saga, inventory, finance, livestock, health, reporting) tiến tới production, mở rộng sang warehouse (V2) và IoT/connected-farm (V3).

## 2. Current status

- **Giai đoạn**: cuối Foundation / giữa **V1 Core Platform**. Chi tiết: [`docs/PROJECT-STATUS.md`](./docs/PROJECT-STATUS.md), [`docs/roadmap.md`](./docs/roadmap.md).
- Build + boot + config ổn định (profile mặc định `dev`, security key nested fix).
- Core domain + order saga + security hoàn chỉnh (verified).
- Còn mở: event-integrity (identity JSON vs protobuf), health service orphan khỏi gateway, prod-ize các facade dev-only, MQTT hardening. Danh sách: [`docs/audit/unresolved-items.md`](./docs/audit/unresolved-items.md).
- ⚠️ Chưa phải hệ thống production.

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
./scripts/release/v1/run-all.sh
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
gateway (edge) → gRPC → services; MQTT event bus (outbox/inbox); Postgres per service + Redis. Sơ đồ + chi tiết: [`docs/architecture/system-overview.md`](./docs/architecture/system-overview.md).

## 6. Gateway mapping overview
Client → gateway REST/GraphQL → gRPC service đích. Bảng route đầy đủ + gap: [`docs/architecture/gateway-mapping.md`](./docs/architecture/gateway-mapping.md).

## 7. Service communication overview
REST (client↔gateway, gateway↔identity auth), gRPC (gateway↔services, order↔inventory/finance saga), MQTT event. Ma trận: [`docs/architecture/service-communications.md`](./docs/architecture/service-communications.md). Luồng request: [`docs/architecture/request-flows.md`](./docs/architecture/request-flows.md).

## 8. Documentation index
→ [`docs/index.md`](./docs/index.md) (kiến trúc · API · services · operations · security · audit · history · backlog).

## 9. Security
JWT RS256 + JWKS, rotating refresh + reuse-detection, TOTP MFA, DB RBAC, gRPC fail-closed authz. Gaps: gRPC TLS off, MQTT broker chưa auth/TLS/ACL, HMAC envelope default-off. Chi tiết: [`SECURITY.md`](./SECURITY.md), [`docs/security/security-architecture.md`](./docs/security/security-architecture.md).

## 10. License
Source-available dưới **PolyForm Strict License 1.0.0** — xem [`LICENSE`](./LICENSE).
Copyright © 2026 Vu Ha Trong — xem [`COPYRIGHT`](./COPYRIGHT).
Chỉ được **xem, nghiên cứu, và chạy** cho mục đích **phi thương mại** được phép. Thương mại hoá, phân phối lại, chỉnh sửa, sublicense, và tạo sản phẩm phái sinh đều **không** được phép trừ khi có uỷ quyền riêng bằng văn bản từ chủ sở hữu bản quyền. Liên hệ cấp phép thương mại qua chủ repository trên GitHub.

## 11. Known limitations
- health-service không có đường qua gateway (orphan).
- finance/reporting/livestock/inventory REST+GraphQL phần lớn **dev-only** (`@Profile dev&!prod`).
- identity publish event JSON (lệch protobuf chuẩn) → không tới WS bridge.
- inventory `AdjustStock`/`TraceLot`, health 6 RPC, farm.proto/telemetry.proto: chưa impl.
- MQTT broker production chưa hardening; gRPC TLS là feature-switch (off).
Đầy đủ: [`docs/audit/unresolved-items.md`](./docs/audit/unresolved-items.md).

## 12. Roadmap (tóm tắt)
**V0 Foundation ✅** → **V1 Core Platform ~ (hiện tại)** → **V2 Warehouse** → **V3 Connected Farm**. Chi tiết + milestone map: [`docs/roadmap.md`](./docs/roadmap.md). Backlog: [`backlog/README.md`](./backlog/README.md).

## 13. TODO checklist còn hiệu lực
- [ ] EVT-01: identity event → protobuf DomainEvent
- [ ] GW-01: route gateway cho health-service
- [ ] ISSUE-02: MQTT HMAC enable + broker auth/TLS/ACL
- [ ] Prod path cho livestock/inventory/finance/reporting (gỡ dev-only)
- [ ] inventory AdjustStock/TraceLot; health 6 RPC còn thiếu
- [ ] gRPC TLS prod; observability cluster-wide; K8s cho 5 service
- [x] Profile mặc định `dev` + security key nested (resolved)

## 14. Layout
```
pom.xml                 parent + aggregator (14 module)
libs/                   common-kernel · security · proto · messaging (không chạy riêng)
platform/               readiness-service · farm-simulator
services/               identity · livestock · health · inventory · finance · order · reporting
apps/smartfarm-gateway  edge REST/GraphQL/WS → gRPC
docs/                   index.md + architecture/api/services/operations/security/audit/history
backlog/                V1 / V2 / V3
```
