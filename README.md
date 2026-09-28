# SmartFarm Platform | Maven monorepo

Đây là **một repository Git duy nhất** — một Maven **monorepo** đa module. Root `pom.xml` vừa là **parent POM**
(mọi module kế thừa cấu hình chung từ nó) vừa là **reactor aggregator** (liệt kê toàn bộ 13 module). Source theo
`com.htv.smartfarm`, Spring Boot 4.1.1, Spring gRPC 1.1.1, **Java 17**.

> Trước đây dự án được tổ chức theo mô hình nhiều repository (mỗi module một repo, ghép bằng aggregator). Nay đã hợp
> nhất thành **một repo** để dễ quản lý phiên bản, xem toàn cảnh kiến trúc và build một phát. Xem `docs/REPOSITORY-STRATEGY.md`
> để hiểu chiến lược monorepo và lý do hợp nhất.

## Bắt đầu nhanh

1. Cài **JDK 17+**, **Maven 3.9+**, Git, Docker Compose và Python 3 (Python chỉ cần cho `doctor.sh`/`bootstrap.sh`).
   Kiểm tra `java -version` và `mvn -version` cùng trỏ về JDK 17+.
2. Clone repo và vào thư mục gốc:
   ```bash
   git clone https://github.com/hatrongvu13/smartfarm-platform-maven-starter.git
   cd smartfarm-platform-maven-starter
   ```
   Không cần clone gì thêm — mọi module đã nằm trong repo này.
3. Kiểm tra môi trường: `./scripts/doctor.sh` (verify toolchain + 13 module trong reactor).
4. Build + cài toàn bộ vào local repo: `./scripts/bootstrap.sh` (tương đương `mvn -B clean install`). Maven Reactor tự
   sắp thứ tự dependency nội bộ.
5. Tùy chọn khởi chạy hạ tầng: `docker compose up -d` (PostgreSQL, Mosquitto, Redis). **H2 dev không cần Docker**.
6. Khởi chạy stack v1 (7 tiến trình, profile `dev`). Thứ tự + port đầy đủ ở
   [`docs/GATEWAY-API-V1.md` §11](./docs/GATEWAY-API-V1.md#11-khởi-động-dev--cho-người-chạy-thử):
   identity(:8092) · livestock(:8081/9091) · inventory(:8083/9093) · finance(:8084/9094) ·
   order(:8085/9095) · reporting(:8086/9096) · gateway(:8080). Ví dụ tối thiểu để smoke test một luồng:
   ```bash
   mvn -f services/smartfarm-identity-service/pom.xml  spring-boot:run   # :8092 (loopback)
   mvn -f services/smartfarm-livestock-service/pom.xml spring-boot:run   # :8081 / gRPC 9091
   mvn -f apps/smartfarm-gateway/pom.xml               spring-boot:run   # :8080 INGRESS
   ```
7. Kiểm nhanh toàn bộ luồng nghiệp vụ: `./scripts/release/v1/run-all.sh`.
8. Kiểm tra: `curl http://localhost:8081/actuator/health` và `curl http://localhost:8080/actuator/health`.
9. GraphQL (đọc, cần token — schema `tasks/task/orders/order`, xem [API §9](./docs/GATEWAY-API-V1.md#9-graphql-đọc-linh-hoạt--post-graphql)):
   ```bash
   curl -s http://localhost:8080/graphql -H "Authorization: Bearer $ACCESS" \
     -H 'Content-Type: application/json' \
     -d '{"query":"{ orders(farmId:\"farm-1\",limit:5){ orderId status totalMinor } }"}'
   ```
   Thử tay ở GraphiQL: [http://localhost:8080/graphiql](http://localhost:8080/graphiql).

### Cách chạy ổn định cho nhiều terminal

Luôn `mvn clean install` từ root một lần trước, để các module nội bộ (`common-kernel`, `security`, `proto`) có mặt
trong local repo; sau đó chạy từng service bằng `-f` (không dùng `-am` với `spring-boot:run`).

```bash
mvn -B clean install
mvn -f services/smartfarm-livestock-service/pom.xml spring-boot:run
# terminal khác
mvn -f apps/smartfarm-gateway/pom.xml spring-boot:run
# terminal khác
mvn -f platform/smartfarm-readiness-service/pom.xml spring-boot:run
# terminal khác
mvn -f platform/smartfarm-farm-simulator/pom.xml spring-boot:run
```

REST mẫu (nền tảng vận hành):

```bash
curl http://localhost:8090/api/v1/platform/readiness      # readiness snapshot
curl -X POST http://localhost:8091/api/v1/simulations/FEEDING   # simulator (trả payload mô phỏng)
```

**Lưu ý (trạng thái hiện tại):** các **service nghiệp vụ v1 đã hoàn thiện** — xem ma trận đầy đủ ở
[`SMARTFARM-VERSION.md`](./SMARTFARM-VERSION.md) và API ở [`docs/GATEWAY-API-V1.md`](./docs/GATEWAY-API-V1.md):
auth/RBAC, livestock (task + monitor quá hạn + schedule generator), inventory, finance, order-saga
(multi-line + warehouse-per-line), reporting (CSV/XLSX/PDF render thật), GraphQL đọc (`/graphql`),
realtime WebSocket (`/ws/events`), Flyway cho 5 DB Postgres. gRPC JWT interceptor + per-service token
đã đăng ký ở các service (zero-trust, audit `actor_id`). TLS gRPC còn là feature-switch (`smartfarm.grpc.tls.enabled=false`),
profile `dev-tls` dùng cert dev. Profile `dev` cho phép smoke test không cần token — **không dùng ở môi trường công khai**.
Hai module nền tảng **readiness** (`GET /api/v1/platform/readiness`) và **farm-simulator**
(`POST /api/v1/simulations/{type}`, hiện trả payload mô phỏng, chưa publish MQTT — có observer subscribe để chẩn đoán)
là **công cụ vận hành**, không phải màn hình nghiệp vụ. Chưa phải hệ thống production.

### Port mặc định (khớp `application.yml` từng module)

Ingress công khai **chỉ** gateway `:8080`; mọi service nghiệp vụ là gRPC nội bộ (loopback), FE không gọi trực tiếp.

| Module | HTTP | gRPC | Ghi chú |
|---|---|---|---|
| **Gateway** (BFF, ingress) | **8080** | — | REST + GraphQL + WS; là gRPC *client*, không mở gRPC server |
| Identity | 8092 | — | HTTP-only, bind loopback (single-ingress); JWKS/login/admin/service-token |
| Livestock | 8081 | 9091 | |
| Inventory | 8083 | 9093 | |
| Finance | 8084 | 9094 | |
| Order | **8085** | **9095** | saga orchestrator |
| Reporting | **8086** | **9096** | export worker + render |
| Health | **8087** | **9097** | domain health (chưa nối FE v1) |
| Readiness | 8090 | 9100 | nền tảng vận hành |
| Farm-simulator | 8091 | 9101 | nền tảng vận hành |
| Hạ tầng | — | — | PostgreSQL 5432, Mosquitto 1883/9001, Redis 6379 |

> Các port từng lệch trong tài liệu cũ (reporting 9095, health 9092, gateway "gRPC 9090") đã được sửa khớp code.
> Đặc biệt health dùng **9097** để không đụng finance **9094** (từng gây `UNIMPLEMENTED` khi hai service tranh cùng port).

### Dev và prod

Dev mặc định dùng **H2** (in-memory hoặc file) ở các service nghiệp vụ; persistence dùng **Spring Data JPA**, schema do
Hibernate sinh (`spring.jpa.hibernate.ddl-auto=update` ở dev). Prod cấu hình `SPRING_PROFILES_ACTIVE=prod`, `*_DB_URL`,
`*_DB_USER`, `*_DB_PASSWORD`, và chạy `ddl-auto=validate`. **Flyway đã quản schema** cho 5 service Postgres
(identity/livestock/inventory/finance/order — baseline `V1__*_baseline.sql` ở `src/main/resources/db/migration/`,
`validate` + baseline-on-migrate); reporting dùng H2 in-mem (dev) nên giữ `ddl-auto`.
**Không dùng Docker Compose local hoặc Mosquitto anonymous ở production.** Mỗi service cần
database/schema riêng. `docker compose down` dừng hạ tầng local.

### Build một module và dependency của nó

```bash
mvn -pl services/smartfarm-livestock-service -am clean verify
mvn -pl apps/smartfarm-gateway -am clean verify
mvn -pl libs/smartfarm-proto -am clean verify
```

### Cấu trúc module & quản lý phiên bản

Đây là monorepo, nên **không có `repositories.json`** và không có bước clone nhiều repo. Cấu hình chung được tập trung
**một chỗ duy nhất** trong root `pom.xml`:

- `<properties>`: `java.version`, `spring-grpc.version`, `grpc.version`, `paho.version`.
- `<dependencyManagement>`: Spring gRPC BOM, 3 module nội bộ (theo `${project.version}`), và các pin bên thứ ba.
- `<pluginManagement>`: `spring-boot-maven-plugin` (repackage).

Mỗi module con kế thừa root qua `<parent>` và **không** khai lại version/Java/BOM. Các module **thư viện**
(`libs/smartfarm-common-kernel`, `libs/smartfarm-security`, `libs/smartfarm-proto`) **không** đóng gói fat-jar (không
chạy độc lập); các service/app mới bật repackage. Đổi một phiên bản dùng chung → sửa đúng một dòng ở root POM.

### Layout

```
pom.xml                     parent + aggregator
libs/                       thư viện dùng chung (không chạy riêng)
  smartfarm-common-kernel
  smartfarm-security
  smartfarm-proto
platform/                   dịch vụ nền tảng
  smartfarm-readiness-service
  smartfarm-farm-simulator
services/                   microservice nghiệp vụ
  smartfarm-livestock-service
  smartfarm-health-service
  smartfarm-inventory-service
  smartfarm-identity-service
  smartfarm-finance-service
  smartfarm-order-service
  smartfarm-reporting-service
apps/
  smartfarm-gateway         BFF REST/GraphQL + WebSocket -> gRPC
```

### Tài liệu

- [`SMARTFARM-VERSION.md`](./SMARTFARM-VERSION.md): ma trận hoàn thiện v1 + gợi ý màn hình FE + backlog v2.
- [`docs/RELEASE-NOTES-v1.md`](./docs/RELEASE-NOTES-v1.md): **release notes v1** — phạm vi, các fix, cách chạy & test.
- [`docs/GATEWAY-API-V1.md`](./docs/GATEWAY-API-V1.md): toàn bộ REST + GraphQL + WebSocket API qua gateway.
- `docs/ARCHITECTURE.md`: kiến trúc, ràng buộc, Saga, security, TLS.
- `docs/SEQUENCES.md`: sequence Mermaid.
- `docs/ROADMAP.md`: các giai đoạn hoàn thiện.
- `docs/REPOSITORY-STRATEGY.md`: chiến lược monorepo — parent+aggregator, quản lý phiên bản tập trung, tách nhiệm vụ module.
