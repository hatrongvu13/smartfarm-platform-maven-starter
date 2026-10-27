# Bảo mật FarmVerse

## Báo lỗ hổng

Không mở issue công khai. Gửi riêng cho người duy trì dự án.

## Hiện trạng

| Lớp | Cơ chế |
|---|---|
| Đăng nhập | OIDC Authorization Code + PKCE (Spring Authorization Server); form login có chọn tenant |
| MFA | TOTP (RFC 6238) — factor `FACTOR_TOTP` trên hạ tầng MFA của Spring Security 7 |
| Mật khẩu | `DelegatingPasswordEncoder` (bcrypt); khóa tạm sau 5 lần sai (cả sai mã TOTP) |
| Token | access 5 phút (`aud=farmverse-api`, `perms`, `roles`, `tenant_id`), refresh 14 ngày xoay vòng |
| Trình duyệt | không giữ token; cookie `FV_SESSION` HttpOnly + SameSite=Lax; CSRF cookie + header |
| Phân quyền | `@PreAuthorize` theo permission; tenant tách bằng claim + Hibernate `@TenantId` |
| Bí mật | prod lấy từ biến môi trường; giá trị trong `application-dev.yml` chỉ cho máy dev |

## Đầu việc còn thiếu

Theo dõi trong [`content/ke-hoach-hoan-thien.md`](content/ke-hoach-hoan-thien.md): mTLS giữa service,
mã khôi phục 2FA, giới hạn theo IP cho trang login, nhật ký bảo mật, xoay khóa ký, header bảo mật ở gateway.
