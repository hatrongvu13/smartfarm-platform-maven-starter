# Chạy FarmVerse trên máy

## Cần có

JDK 17+, Maven 3.9+, Docker (cho PostgreSQL + Redis), Node 20+.

## Thứ tự

1. `docker compose up -d` — tạo database `farmverse_identity`, `farmverse_farm` và Redis (mật khẩu `farmverse-dev`).
   Nếu volume postgres đã có từ trước, tạo tay: `CREATE DATABASE farmverse_identity; CREATE DATABASE farmverse_farm;`
2. `./scripts/generate-dev-keys.sh` — cặp khóa RSA ký JWT, nằm trong `services/identity-service/src/main/resources/.local/` (đã gitignore).
3. `./scripts/generate-dev-mtls.sh` — CA dev + cert cho từng service trong `.local/mtls/` (gitignore). Tin `ca.crt` trong hệ điều hành để mở trang login `https://localhost:8092` không bị cảnh báo.
4. identity (`:8092`) → farm (`:8082`) → gateway (`:8080`) → `npm run dev` trong `apps/farmverse-web` (`:5173`).
5. Mở `http://127.0.0.1:8080`.

Health (không TLS, chỉ loopback): identity `:9092`, farm `:9082` — `/actuator/health`.

## Biến môi trường hay dùng

| Biến | Mặc định dev | Dùng ở |
|---|---|---|
| `IDENTITY_ISSUER` | `http://localhost:8092` | identity, farm, gateway |
| `GATEWAY_CLIENT_SECRET` | giá trị dev trong YAML | identity, gateway |
| `IDENTITY_ADMIN_EMAIL` / `IDENTITY_ADMIN_PASSWORD` | `admin@farmverse.local` / `Admin@123456789` | identity (super admin đầu tiên) |
| `IDENTITY_MFA_ENCRYPTION_PASSWORD` / `_SALT` | giá trị dev | identity (mã hóa secret TOTP) |
| `REDIS_PASSWORD` | `farmverse-dev` | gateway |
| `FARMVERSE_MTLS_DIR` | `.local/mtls` (tính từ thư mục module) | mọi service |

## Test

```bash
mvn -B test                                   # không cần Docker (H2)
mvn -B verify                                 # thêm *IT trên PostgreSQL/Redis thật (cần Docker)
```
