# Chạy FarmVerse trên máy

## Cần có

JDK 17+, Maven 3.9+, Docker (cho PostgreSQL + Redis), Node 20+.

## Thứ tự

1. `docker compose up -d` — PostgreSQL chạy `infra/postgres-init.sh`: database `farmverse_identity`, `farmverse_farm`, mỗi cái hai vai trò
   (`<svc>_owner` cho Flyway, `<svc>_app` cho ứng dụng; mật khẩu dev `<svc>-owner-dev` / `<svc>-app-dev`). Redis có mật khẩu `farmverse-dev`.
   **Volume postgres có từ trước M4** (service báo lỗi đăng nhập `identity_app`/`farm_app`): chạy lại script, không mất dữ liệu:
   `docker compose exec -T postgres bash < infra/postgres-init.sh`
2. `./scripts/generate-dev-keys.sh` — cặp khóa RSA ký JWT, nằm trong `services/identity-service/src/main/resources/.local/` (đã gitignore).
3. mTLS (tùy chọn ở dev, mặc định TẮT; prod luôn bật):
   - Tắt (mặc định): `SPRING_PROFILES_ACTIVE=dev`. Mọi service chạy HTTP, identity ở `http://localhost:8092`.
   - Bật: chạy `./scripts/generate-dev-mtls.sh`, rồi đặt `SPRING_PROFILES_ACTIVE=dev,mtls` cho **cả ba** service
     (identity, farm, gateway). Identity chuyển sang `https://localhost:8092`. Tin `ca.crt` trong hệ điều hành để trình duyệt không cảnh báo.
   - Ba service phải cùng bật hoặc cùng tắt, nếu không chúng gọi nhau sai giao thức (http/https).
4. identity (`:8092`) → farm (`:8082`) → gateway (`:8080`) → `npm run dev` trong `apps/farmverse-web` (`:5173`).
5. Mở `http://127.0.0.1:8080`.

Health (không TLS, chỉ loopback): gateway `:9080` (luôn), identity `:9092`, farm `:9082` (khi bật `mtls`) — `/actuator/health`.
gRPC: identity ở `:9093` (farm gọi `IdentityDirectory`; mTLS khi bật `mtls`, plaintext loopback khi tắt). `IDENTITY_GRPC_PORT` đổi cổng; **không** dùng 9090 (trùng cổng quản trị trong container). Xem [`docs/patterns/grpc.md`](../patterns/grpc.md).
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

`*IT` dùng Testcontainers (PostgreSQL 17, Redis 8). Với Colima trên macOS:

```bash
colima start
export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
mvn -B verify
```

`*IT` tự dựng container ở cổng ngẫu nhiên, không đụng `compose.yaml` đang chạy.
