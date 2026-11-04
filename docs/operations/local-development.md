# Chạy FarmVerse trên máy

## Cần có

JDK 17+, Maven 3.9+, Docker (cho PostgreSQL + Redis), Node 20+.

## Thứ tự

1. `docker compose up -d` — tạo database `farmverse_identity`, `farmverse_farm` và Redis (mật khẩu `farmverse-dev`).
   Nếu volume postgres đã có từ trước, tạo tay: `CREATE DATABASE farmverse_identity; CREATE DATABASE farmverse_farm;`
2. `./scripts/generate-dev-keys.sh` — cặp khóa RSA ký JWT, nằm trong `services/identity-service/src/main/resources/.local/` (đã gitignore).
3. mTLS (tùy chọn ở dev, mặc định TẮT; prod luôn bật):
   - Tắt (mặc định): `SPRING_PROFILES_ACTIVE=dev`. Mọi service chạy HTTP, identity ở `http://localhost:8092`.
   - Bật: chạy `./scripts/generate-dev-mtls.sh`, rồi đặt `SPRING_PROFILES_ACTIVE=dev,mtls` cho **cả ba** service
     (identity, farm, gateway). Identity chuyển sang `https://localhost:8092`. Tin `ca.crt` trong hệ điều hành để trình duyệt không cảnh báo.
   - Ba service phải cùng bật hoặc cùng tắt, nếu không chúng gọi nhau sai giao thức (http/https).
4. identity (`:8092`) → farm (`:8082`) → gateway (`:8080`) → `npm run dev` trong `apps/farmverse-web` (`:5173`).
5. Mở `http://127.0.0.1:8080`.

Health (không TLS, chỉ loopback): gateway `:9080` (luôn), identity `:9092`, farm `:9082` (khi bật `mtls`) — `/actuator/health`.
Cổng công khai `:8080` của gateway không có `/actuator/**`.

## Biến môi trường hay dùng

| Biến | Mặc định dev | Dùng ở |
|---|---|---|
| `IDENTITY_ISSUER` | `http://localhost:8092` | identity, farm, gateway |
| `GATEWAY_CLIENT_SECRET` | giá trị dev trong YAML | identity, gateway |
| `IDENTITY_ADMIN_EMAIL` / `IDENTITY_ADMIN_PASSWORD` | `admin@farmverse.local` / `Admin@123456789` | identity (super admin đầu tiên) |
| `IDENTITY_MFA_ENCRYPTION_PASSWORD` / `_SALT` | giá trị dev | identity (mã hóa secret TOTP) |
| `REDIS_PASSWORD` | `farmverse-dev` | gateway và `compose.yaml` (đổi một chỗ, cả hai cùng đọc) |
| `GATEWAY_SESSION_TIMEOUT` | `30m` | gateway |
| `FARMVERSE_MTLS_DIR` | `.local/mtls` (tính từ thư mục đang chạy; IntelliJ = gốc repo). Nên đặt đường dẫn tuyệt đối | mọi service (khi bật `mtls`) |

## Test

```bash
mvn -B test                                   # không cần Docker (H2)
mvn -B verify                                 # thêm *IT trên PostgreSQL/Redis thật (cần Docker)
```
