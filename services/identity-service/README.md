# identity-service

OIDC Provider của FarmVerse (Spring Authorization Server) cùng REST quản trị người dùng, vai trò và tổ chức. Java 17, Spring Boot 4, PostgreSQL.

Gateway (BFF) là client duy nhất của trình duyệt. Trình duyệt chỉ vào identity để **đăng nhập** (form + TOTP) và **đăng xuất**. Mọi API khác đi qua gateway với access token.

## Chạy

Xem [`docs/operations/local-development.md`](../../docs/operations/local-development.md). Tóm tắt:

```bash
docker compose up -d                                  # PostgreSQL (farmverse_identity, vai trò identity_owner / identity_app)
./scripts/generate-dev-keys.sh                        # khóa RSA ký JWT, một lần
mvn -pl services/identity-service spring-boot:run     # http://localhost:8092 (profile dev)
```

Tài khoản dev: `admin@farmverse.local` / `Admin@123456789` (`IDENTITY_ADMIN_EMAIL` / `IDENTITY_ADMIN_PASSWORD`, chỉ dùng lần khởi tạo đầu).

| Profile | Dùng khi | Ghi chú |
|---|---|---|
| `dev` | Chạy trên máy | Swagger UI ở `/swagger-ui.html`; giá trị mặc định cho mọi bí mật |
| `test` | `mvn test` | H2, khóa ký sinh tạm thời |
| `mtls` | Bật mTLS giữa các service | Cổng ứng dụng HTTPS, cần cert client; actuator ở `:9092` (loopback) |
| `prod` | Production | Tự bật `mtls`; mọi bí mật từ biến môi trường, không có mặc định ([`.env.production.example`](../../.env.production.example)); log JSON (ECS) |

## Endpoint

| Nhóm | Đường dẫn | Ai gọi |
|---|---|---|
| OIDC / OAuth2 | `/.well-known/openid-configuration`, `/oauth2/authorize`, `/oauth2/token`, `/oauth2/revoke`, `/oauth2/jwks`, `/userinfo`, `/connect/logout` | Gateway, resource server (JWKS) |
| Đăng nhập (HTML) | `GET/POST /login`, `GET/POST /login/totp` | Trình duyệt |
| Hồ sơ của tôi | `/me`, `/me/password`, `/me/mfa/**` | SPA qua gateway |
| Quản trị | `/users/**`, `/roles/**`, `/permissions/**`, `/tenants/**` | SPA qua gateway, theo `perms` |
| gRPC `:9093` | `farmverse.identity.v1.IdentityDirectory`: `GetMember`, `BatchGetMembers` | Farm (mTLS, SAN `farm`) mang access token của người dùng ([ADR-0014](../../docs/architecture/adr/0014-grpc-service-calls.md)) |

Chi tiết API và dạng lỗi (ProblemDetail, `code` dạng `SF-<MIEN>-<SO>`): [`docs/operations/identity-api.md`](../../docs/operations/identity-api.md).

## Token

| Token | Thời hạn | Ghi chú |
|---|---|---|
| Access token (JWT RS256) | 5 phút | `aud=[farm-service, identity-service]` ([ADR-0006](../../docs/architecture/adr/0006-per-service-audience.md)), `scope`, `tenant_id`, `membership_id`, `roles`, `perms`, `token_type` |
| Refresh token | 14 ngày | Xoay vòng mỗi lần dùng. Dùng lại token cũ (quá 30 giây sau lần xoay) thì thu hồi cả authorization. Gateway thu hồi khi đăng xuất ([ADR-0013](../../docs/architecture/adr/0013-token-revocation.md)) |
| ID token | theo SAS | Gateway dùng làm `id_token_hint` khi đăng xuất |

Access token không thu hồi tức thì được: service kiểm JWT tại chỗ bằng JWKS cache. Khóa tài khoản hay đổi quyền chỉ có hiệu lực khi token cũ hết hạn, tối đa sau 5 phút. Xoay khóa ký: [`docs/operations/key-rotation.md`](../../docs/operations/key-rotation.md).

## Bảo mật

| Việc | Cách làm | Test |
|---|---|---|
| Đăng nhập theo tổ chức | Email + mật khẩu + mã tổ chức; principal là membership | `AuthorizationCodeFlowTest` |
| Mật khẩu | `{bcrypt}` qua `DelegatingPasswordEncoder`; mật khẩu mới dài 12–128 ký tự | `ValidationProblemTest` |
| Khóa tài khoản | 5 lần sai thì khóa 15 phút; đăng nhập đúng thì đếm lại; quản trị viên mở khóa được | `AccountLockoutTest` |
| Giới hạn theo IP | `POST /login`, `POST /login/totp`: 10 lần/phút/IP. Bộ đếm ở PostgreSQL, chung mọi instance; database lỗi thì 503 | `LoginRateLimitFilterTest`, `LoginRateLimitPostgresIT` |
| TOTP | Tài khoản đã bật TOTP phải nhập mã trước khi được cấp code; secret mã hóa AES-GCM; chống dùng lại mã; mã khôi phục dùng một lần | `TotpLoginFlowTest`, `RecoveryCodeServiceTest` |
| Nhật ký bảo mật | Logger `SECURITY_AUDIT`, giá trị được làm sạch, không ghi mật khẩu/mã/token | `SecurityAuditTest`, `AdminAuditTest` |
| Không lộ bí mật trong log | Logger in URL redirect (có `?code=`) bị ghim ở INFO | `IdentityObservabilityTest` ([ADR-0012](../../docs/architecture/adr/0012-observability.md)) |
| Database | Flyway chạy bằng `identity_owner`, ứng dụng chạy bằng `identity_app` (chỉ DML) | `PostgresFlowsIT` |

## Mã nguồn

| Package | Việc |
|---|---|
| `oauth2` | Đăng ký client (`OAuthClientProvisioner`), claim tùy biến (`FarmVerseTokenCustomizer`), khóa ký |
| `login` | Form login có tổ chức, TOTP, giới hạn theo IP |
| `account`, `tenant`, `authorization` | Người dùng, tổ chức và membership, vai trò và quyền |
| `mfa` | TOTP và mã khôi phục |
| `audit` | Nhật ký bảo mật |
| `web` | REST quản trị, ánh xạ lỗi |
| `config` | Security filter chain, mTLS, OpenAPI, thuộc tính `farmverse.identity.*` |
| `bootstrap` | Seed danh mục quyền và super admin đầu tiên |
| `directory`, `grpc` | Danh bạ thành viên chỉ-đọc và server gRPC của nó (mapper, bảo mật, guard prod) |

Thiết kế chung: [`docs/patterns/oidc-sas.md`](../../docs/patterns/oidc-sas.md). Kiến trúc hiện tại: [`docs/architecture/current-architecture.md`](../../docs/architecture/current-architecture.md).

## Test

```bash
mvn -pl services/identity-service test      # H2, không cần Docker
mvn -pl services/identity-service verify    # thêm PostgresFlowsIT trên PostgreSQL 17 thật (cần Docker)
```

## Chưa có

- Quên mật khẩu, xác minh email, CAPTCHA.
- gRPC: identity đã có server (P2); farm chưa gọi (P3). Thiết kế ở [`content/thiet-ke-farmverse.md`](../../content/thiet-ke-farmverse.md), mục 6.
- Token riêng cho từng service (ADR-0006 bước 2).
