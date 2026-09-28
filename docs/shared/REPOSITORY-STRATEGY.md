# Chiến lược repository & quản lý module

## Mô hình: monorepo một repository

Dự án là **một repository Git duy nhất** tổ chức theo Maven multi-module (monorepo). Toàn bộ module — thư viện dùng
chung, dịch vụ nền tảng, microservice nghiệp vụ và gateway — nằm chung trong cây nguồn này và được build bởi một
reactor từ root `pom.xml`.

> Trước đây dự án dự kiến chia thành nhiều repository (mỗi module một repo, ghép bằng manifest `repositories.json` +
> `bootstrap.sh` clone). Mô hình đó đã được **thay bằng monorepo** để: xem toàn cảnh kiến trúc trong một nơi, quản lý
> phiên bản tập trung, build/test một phát, và tránh version drift giữa các repo. `repositories.json` và bước clone
> nhiều repo không còn tồn tại.

### Vì sao monorepo (ở quy mô hiện tại)

- Các module gắn kết chặt qua proto contract + saga cross-service; xem chúng cùng một chỗ có giá trị hơn tách rời.
- Đổi contract (`proto`) và các consumer của nó trong **cùng một commit** — không phải điều phối nhiều PR chéo repo.
- Chưa có nhu cầu release/deploy độc lập từng service, cũng chưa có nhiều team sở hữu riêng — hai điều kiện chính để
  tách repo.

## Maven parent + aggregator

Root `pom.xml` đóng **hai vai trò**:

1. **Parent POM** — mọi module kế thừa qua `<parent>`. Cấu hình chung tập trung một chỗ:
   - `<properties>`: `java.version`, `spring-grpc.version`, `grpc.version`, `paho.version`.
   - `<dependencyManagement>`: Spring gRPC BOM; 3 module nội bộ theo `${project.version}`; pin bên thứ ba.
   - `<pluginManagement>`: `spring-boot-maven-plugin` (repackage).
2. **Aggregator (reactor)** — liệt kê toàn bộ module trong `<modules>`; Maven tự sắp thứ tự build theo dependency nội bộ.

Module con **không** khai lại version/Java/BOM. Đổi một phiên bản dùng chung → sửa đúng một dòng ở root POM.

## Versioning

- Toàn bộ module dùng chung một version (`${project.version}`, hiện `0.1.0-SNAPSHOT`) — release đồng bộ cả monorepo.
- Không cần publish `common-kernel`/`proto`/`security` ra registry: reactor build và cài chúng vào local repo khi
  `mvn install` từ root.
- **Proto ưu tiên backward compatible**: không tái sử dụng field number; chỉ additive change trong cùng `v1`; breaking
  change tạo `v2`.
- Nếu sau này một service cần release/deploy độc lập, cân nhắc tách nó ra repo riêng **khi đó** — kèm điều kiện service
  đã đủ dày và có ranh giới sở hữu rõ ràng.

## Module thư viện (không chạy riêng)

Các module trong `libs/` là **thư viện**, được các module khác phụ thuộc, **không** đóng gói fat-jar và không chạy độc
lập (pom của chúng không bật `spring-boot-maven-plugin` repackage):

- `common-kernel`: event envelope, error model, identifiers, outbox abstractions. **Không** chứa business entity.
- `security`: resource-server config, JWT validators, gRPC interceptors, method authorization.
- `proto`: protobuf/gRPC contract (sinh stub). **Không** trộn entity JPA.

Module nền tảng trong `platform/`:

- `readiness`: aggregation sức khỏe qua gRPC fan-out; không trở thành service discovery.
- `farm-simulator`: phát telemetry/status giả lập; không phụ thuộc domain internals.

## CI

Một pipeline CI duy nhất build cả reactor từ root: `mvn -B clean verify`. Chạy unit/integration/contract test cho mọi
module trong một lần, không cần khóa manifest commit/tag chéo repo như mô hình cũ. End-to-end test dựng các service từ
cùng cây nguồn nên luôn nhất quán phiên bản.
