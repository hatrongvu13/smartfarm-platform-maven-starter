# SmartFarm Platform | Maven workspace

Root repository là **Maven aggregator**, ghép 11 repository độc lập bằng `pom.xml` và Maven Reactor. Source mẫu theo
`com.htv.smartfarm`, Spring Boot 4.1.1, Spring gRPC 1.1.1, Java 21.

## Bắt đầu nhanh

1. Cài **JDK 17+**, **Maven 3.9+**, Git, Docker Compose và Python 3 (chỉ cần cho bootstrap/doctor). Kiểm tra
   `java -version` và `mvn -version` cùng trỏ về JDK 17+.
2. Giải nén workspace. Nếu đã tạo các repository Git thật, chạy
   `SMARTFARM_GIT_BASE=git@github.com:your-org ./scripts/bootstrap.sh` để clone repo chưa tồn tại. Bản ZIP đã có sẵn
   source mẫu nên script sẽ bỏ qua thư mục không rỗng; để chuyển sang repo Git thật, hãy tạo repo từ các thư mục mẫu
   trước, hoặc clone vào workspace root sạch.
3. Kiểm tra `./scripts/doctor.sh`.
4. Build toàn bộ: `./scripts/build-all.sh` hoặc `mvn -B clean verify`. Maven Reactor tự sắp thứ tự các dependency nội
   bộ.
5. Tùy chọn khởi chạy hạ tầng: `docker compose up -d` (PostgreSQL, Mosquitto, Redis). **H2 dev không cần Docker**.
6. Mở terminal 1: `mvn -f services/smartfarm-livestock-service/pom.xml spring-boot:run` sau khi đã `mvn install` từ
   root; hoặc không chạy `spring-boot:run` với `-am` vì goal này có thể khởi chạy các upstream module; dùng lệnh
   terminal 1 sau install.
7. Mở terminal 2: `mvn -f apps/smartfarm-gateway/pom.xml spring-boot:run`.
8. Kiểm tra: `curl http://localhost:8081/actuator/health` và `curl http://localhost:8080/actuator/health`.
9. GraphQL:
   `curl -s http://localhost:8080/graphql -H 'Content-Type: application/json' -d '{"query":"{ platformStatus { name status } }"}'`.

### Cách chạy ổn định cho nhiều terminal

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

**Lưu ý:** readiness hiện trả `DEGRADED` cố ý vì chưa có gRPC fan-out; simulator REST mới trả payload mô phỏng, **chưa
publish MQTT**; Gateway GraphQL chỉ có query demo, chưa proxy gRPC; Gateway profile `dev` cho phép truy cập không cần
token để smoke test, không sử dụng profile này trên môi trường công khai; JWT interceptor mới là library mẫu, chưa đăng
ký auth thật; TLS có placeholder nhưng chưa được bind vào gRPC transport. Không sử dụng starter này làm hệ thống
production.

### Port mặc định

- Gateway HTTP 8080, gRPC 9090.
- Livestock HTTP 8081, gRPC 9091.
- Health 8082/9092, Inventory 8083/9093, Finance 8084/9094, Reporting 8085/9095.
- Readiness 8090/9100, Simulator 8091/9101.
- PostgreSQL 5432, Mosquitto 1883/9001, Redis 6379.

### Dev và prod

Dev mặc định dùng H2 in-memory ở các service nghiệp vụ. Prod cấu hình `SPRING_PROFILES_ACTIVE=prod`, `DB_URL`,
`DB_USERNAME`, `DB_PASSWORD`; **không dùng cấu hình Docker Compose local hoặc Mosquitto anonymous ở production**. Mỗi
service cần database/schema riêng trước khi triển khai thật. `docker compose down` dừng hạ tầng local.

### Build một service và dependency

```bash
mvn -pl services/smartfarm-livestock-service -am clean verify
mvn -pl apps/smartfarm-gateway -am clean verify
mvn -pl libs/smartfarm-proto -am clean verify
```

### Git repository và version

`repositories.json` chứa đường dẫn của 11 repo. Root aggregator **không phải parent POM** của repo con; `.gitignore` của
root bỏ qua source của các repo con để tránh commit nhầm; mỗi repo kế thừa `spring-boot-starter-parent` và có thể
`mvn verify` độc lập sau khi các common artifact đã được publish hoặc `mvn install` từ workspace. Các phiên bản shared
hiện pin trong từng POM để bảo đảm build độc lập. Khi vận hành lâu dài nên tách thêm một repo BOM/parent được publish,
tránh drift phiên bản.

### Tài liệu

- `docs/ARCHITECTURE.md`: kiến trúc, ràng buộc, Saga, security, TLS.
- `docs/SEQUENCES.md`: sequence Mermaid.
- `docs/ROADMAP.md`: các giai đoạn hoàn thiện.
- `docs/REPOSITORY-STRATEGY.md`: chiến lược repo và version.
