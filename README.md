# FarmVerse

Hệ sinh thái học microservices với Spring Boot 4.1.2 + Spring Cloud 2025.1.4.

Giai đoạn hiện tại: **hoàn thiện nền tảng** (identity, gateway, giao diện) tới 10/10 trước khi mở thêm miền
nghiệp vụ. Kế hoạch: [`content/thiet-ke-farmverse.md`](content/thiet-ke-farmverse.md).

| Module | Cổng | Vai trò |
|---|---|---|
| `platform/api-gateway` | 8080 | BFF: đăng nhập OIDC thay trình duyệt, TokenRelay, CSRF, rate limit |
| `services/identity-service` | 8092 | OIDC provider (Spring Authorization Server), tenant, role, permission, TOTP |
| `services/farm-service` | 8082 | service nghiệp vụ mẫu: hexagonal + DDD + `@TenantId` |
| `libs/farmverse-starter-service` | — | phần dùng chung của resource server |
| `apps/farmverse-web` | 5173 | React SPA (mở qua gateway) |

## Chạy trên máy

```bash
docker compose up -d                      # postgres (farmverse_identity, farmverse_farm) + redis
./scripts/generate-dev-keys.sh            # khóa ký JWT cho identity (một lần)
# mTLS ở dev là tùy chọn (mặc định tắt). Muốn bật: chạy script rồi thêm profile mtls cho cả ba service:
#   ./scripts/generate-dev-mtls.sh && export SPRING_PROFILES_ACTIVE=dev,mtls
mvn -B install -DskipTests

mvn -pl services/identity-service spring-boot:run     # 1. trước tiên
mvn -pl services/farm-service spring-boot:run
mvn -pl platform/api-gateway spring-boot:run          # sau identity (discovery)
(cd apps/farmverse-web && npm install && npm run dev)
```

Mở **http://127.0.0.1:8080** (không dùng `localhost`: SAS không nhận redirect URI có host `localhost`).
Khi bật `mtls`, trang login của identity ở `https://localhost:8092` dùng CA dev: tin `.local/mtls/ca.crt` trong hệ điều hành
(lệnh in ra sau khi chạy script) để trình duyệt không cảnh báo. Tài khoản dev: `admin@farmverse.local` / `Admin@123456789`.

## Tài liệu

- Đọc mã theo luồng, có cả pattern: [`docs/code-walkthrough.md`](docs/code-walkthrough.md)
- Chạy dev: [`docs/operations/local-development.md`](docs/operations/local-development.md)
- Bảo mật: [`SECURITY.md`](SECURITY.md)
