# Identity: tài liệu API và dạng lỗi

## OpenAPI

- Dev: Swagger UI ở `http://localhost:8092/swagger-ui.html`, JSON ở `/v3/api-docs`. Bấm **Authorize**, dán access token (không cần chữ `Bearer`).
- Prod: tắt (`springdoc.api-docs.enabled=false` trong `application.yml`), các đường này trả 404.
- Chỉ mô tả REST quản trị (`/me`, `/users`, `/roles`, `/permissions`, `/tenants`). Endpoint OAuth2/OIDC xem `/.well-known/openid-configuration`.
- Cấu hình: `OpenApiConfiguration`. Mọi thao tác tự có phản hồi 400, 401, 403 trỏ tới schema `Problem`.

## Lỗi trả về

Mọi lỗi là RFC 9457 ProblemDetail (`application/problem+json`) có field `code` dạng `SF-<MIEN>-<SO>`.

| Tình huống | Status | `code` |
|---|---|---|
| Thiếu hoặc sai token | 401 | `SF-AUTH-001` |
| Không đủ quyền | 403 | `SF-AUTH-002` |
| Quá nhiều lần thử đăng nhập | 429 | `SF-AUTH-003` |
| Dữ liệu không hợp lệ (`@Valid`) | 400 | `SF-VALIDATION-001`, kèm `errors: [{field, message}]` |
| Lỗi MVC khác (JSON hỏng, sai method...) | 4xx | `SF-HTTP-<status>` |
| Lỗi nghiệp vụ | 404, 409... | `SF-IDENTITY-xxx` |

Ví dụ lỗi validation:

```json
{
  "type": "urn:farmverse:problem:SF-VALIDATION-001",
  "title": "Bad Request",
  "status": 400,
  "detail": "Dữ liệu gửi lên không hợp lệ",
  "code": "SF-VALIDATION-001",
  "errors": [{ "field": "email", "message": "must be a well-formed email address" }]
}
```

Lỗi validation **không** ghi lại giá trị đã gửi, vì có thể là mật khẩu hoặc mã TOTP.

## Cách nối vào service khác

Lớp dùng chung là `ProblemExceptionHandler` trong `farmverse-starter-service` (kế thừa `ResponseEntityExceptionHandler` của Spring, không có auto-configuration). Service tạo một lớp con gắn `@RestControllerAdvice`, như `IdentityExceptionHandler`.

## Nhật ký bảo mật (`SECURITY_AUDIT`)

Mọi sự kiện đi qua `SecurityAudit`. Mỗi sự kiện là một dòng `event=... actor=... key=value` và các field riêng
(log JSON ở prod, `logging.structured.format.console=ecs`). `actor` là người đang đăng nhập (`sub` của token);
`system` khi hệ thống tự làm (khóa tạm).

| Nhóm | Sự kiện | Field chính |
|---|---|---|
| Đăng nhập | `authentication.success`, `authentication.failure`, `authorization.denied` | `actor`, `reason` |
| Khóa tài khoản | `account.locked` (đúng lúc bị khóa, `actor=system`), `account.unlocked` | `target`, `duration`, `tenant` |
| Người dùng | `user.added`, `user.status.changed`, `user.roles.replaced` | `tenant`, `target`, `from`, `to` |
| Role | `role.created`, `role.updated`, `role.deleted` | `tenant`, `role`, `permissions` (hoặc `permissionsFrom`, `permissionsTo`) |
| Permission, tenant | `permission.created/updated/deleted`, `tenant.created` | `permission`, `tenantCode` |
| 2FA | `mfa.totp.enabled`, `mfa.totp.disabled`, `mfa.totp.reset`, `mfa.recovery.generated`, `mfa.recovery.used` | `target`, `remaining` |

Quy tắc: không bao giờ ghi mật khẩu, mã TOTP, mã khôi phục hay token. Giá trị do người gọi nhập được làm sạch
(bỏ xuống dòng và khoảng trắng, cắt 80 ký tự). Dòng log được ghi trong giao dịch, trước khi commit.
