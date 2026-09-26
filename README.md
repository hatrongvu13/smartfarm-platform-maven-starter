# SmartFarm Platform | Maven monorepo

Đây là **một repository Git duy nhất** — một Maven **monorepo** đa module. Root `pom.xml` vừa là **parent POM**
(mọi module kế thừa cấu hình chung từ nó) vừa là **reactor aggregator** (liệt kê toàn bộ 12 module). Source theo
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
3. Kiểm tra môi trường: `./scripts/doctor.sh` (verify toolchain + 12 module trong reactor).
4. Build + cài toàn bộ vào local repo: `./scripts/bootstrap.sh` (tương đương `mvn -B clean install`). Maven Reactor tự
   sắp thứ tự dependency nội bộ.
5. Tùy chọn khởi chạy hạ tầng: `docker compose up -d` (PostgreSQL, Mosquitto, Redis). **H2 dev không cần Docker**.
6. Terminal 1: `mvn -f services/smartfarm-livestock-service/pom.xml spring-boot:run` (sau khi đã `mvn install` từ root).
7. Terminal 2: `mvn -f apps/smartfarm-gateway/pom.xml spring-boot:run`.
8. Kiểm tra: `curl http://localhost:8081/actuator/health` và `curl http://localhost:8080/actuator/health`.
9. GraphQL:
   `curl -s http://localhost:8080/graphql -H 'Content-Type: application/json' -d '{"query":"{ platformStatus { name status } }"}'`.

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

REST mẫu:

```bash
curl http://localhost:8090/api/v1/readiness
curl -X POST http://localhost:8091/api/v1/simulations/FEEDING
```

**Lưu ý (trạng thái hiện tại):** readiness trả `DEGRADED` cố ý vì chưa fan-out gRPC thật; simulator REST trả payload mô
phỏng, **chưa publish MQTT**; Gateway GraphQL mới có query demo, chưa proxy gRPC; profile `dev` cho phép truy cập không
cần token để smoke test — **không dùng profile này ở môi trường công khai**; JWT gRPC interceptor là thư viện mẫu, chưa
được đăng ký ở các service; TLS mới là placeholder, chưa bind vào gRPC transport. Không dùng làm hệ thống production.

### Port mặc định

- Gateway HTTP 8080, gRPC 9090.
- Livestock HTTP 8081, gRPC 9091.
- Health 8082/9092, Inventory 8083/9093, Finance 8084/9094, Reporting 8085/9095.
- Identity HTTP 8092.
- Readiness 8090/9100, Simulator 8091/9101.
- PostgreSQL 5432, Mosquitto 1883/9001, Redis 6379.

### Dev và prod

Dev mặc định dùng **H2** (in-memory hoặc file) ở các service nghiệp vụ; persistence dùng **Spring Data JPA**, schema do
Hibernate sinh (`spring.jpa.hibernate.ddl-auto=update` ở dev). Prod cấu hình `SPRING_PROFILES_ACTIVE=prod`, `*_DB_URL`,
`*_DB_USER`, `*_DB_PASSWORD`, và chạy `ddl-auto=validate` — schema prod phải được tạo/migrate ngoài (khuyến nghị Flyway
ở giai đoạn sau). **Không dùng Docker Compose local hoặc Mosquitto anonymous ở production.** Mỗi service cần
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
  smartfarm-reporting-service
apps/
  smartfarm-gateway         BFF REST/GraphQL -> gRPC
```

### Tài liệu

- `docs/ARCHITECTURE.md`: kiến trúc, ràng buộc, Saga, security, TLS.
- `docs/SEQUENCES.md`: sequence Mermaid.
- `docs/ROADMAP.md`: các giai đoạn hoàn thiện.
- `docs/REPOSITORY-STRATEGY.md`: chiến lược monorepo — parent+aggregator, quản lý phiên bản tập trung, tách nhiệm vụ module.
