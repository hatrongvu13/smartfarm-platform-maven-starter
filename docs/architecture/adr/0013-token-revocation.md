# ADR-0013: Đăng xuất thu hồi refresh token, phát hiện dùng lại refresh token

**Trạng thái:** Đã chốt (I1, 2026-11-04).

**Bối cảnh.** Rà I1 ("revoke và RP logout") bằng probe trên identity thật (MockMvc + H2) cho thấy:

| Kiểm | Kết quả trước khi sửa |
|---|---|
| Sau RP-initiated logout, refresh token còn dùng được? | **Còn** (200), tới 14 ngày |
| Sau logout, access token còn dùng được? | Còn, tới khi hết hạn (5 phút). Đúng thiết kế: JWT kiểm tại chỗ |
| Dùng lại refresh token cũ (đã xoay) thì token mới có bị thu hồi? | **Không** (token mới vẫn 200) |
| Một lần sai mật khẩu đếm mấy lần? | **Hai lần**: tài khoản bị khóa sau 3 lần sai thay vì 5 |

Refresh token còn sống sau khi đăng xuất nghĩa là bản sao bị lộ (Redis, log, backup) dùng được thêm 14 ngày. RFC 9700 §4.14.2 khuyên: khi xoay vòng refresh token, lần dùng lại token cũ phải thu hồi cả họ token.

**Quyết định.**
1. **Gateway thu hồi khi đăng xuất.** `RevokeTokensOnLogoutHandler` gửi `POST /oauth2/revoke` (RFC 7009, client auth Basic, qua cùng `WebClient` mTLS như đổi code) với refresh token trong phiên. Handler chạy *trước* khi xóa security context và phiên. Nếu identity lỗi hay chậm (timeout 3 giây), vẫn đăng xuất và ghi cảnh báo; không giữ người dùng ở trạng thái đăng nhập. SAS thu hồi refresh token thì cũng vô hiệu access token của cùng authorization ở phía identity.
2. **Identity phát hiện dùng lại.** `RefreshTokenReuseDetection` bọc `JdbcOAuth2AuthorizationService`:
   - Khi lưu một authorization mà refresh token đã đổi, ghi hash SHA-256 của token cũ vào `oauth2_rotated_refresh_token` (V4). Bảng có khóa ngoại tới `oauth2_authorization`, `ON DELETE CASCADE`.
   - Khi gặp một refresh token không tìm thấy nhưng có trong bảng đó, và lần xoay đã quá `farmverse.identity.refresh-token-reuse-grace` (mặc định 30 giây): xóa cả authorization và ghi `token.refresh_reuse_detected` vào nhật ký bảo mật.
   - **Khoảng grace** dành cho trường hợp gateway gửi hai lần refresh song song với cùng một token, khi nhiều request đến lúc access token vừa hết hạn. Lần thứ hai trong khoảng này chỉ nhận `invalid_grant` như trước, người dùng không bị đăng xuất.
3. **Đếm sai mật khẩu đúng một lần.** `TenantAuthenticationProvider` không còn là bean. Spring Security gắn bean `AuthenticationProvider` duy nhất vào `AuthenticationManager` cha, nên mật khẩu sai bị kiểm hai lần: provider của chain thất bại, rồi manager cha thử lại.

**Phương án đã bỏ.**
- Thu hồi ở identity khi nhận `/connect/logout`: `id_token_hint` có thể là id token cũ (từ lúc đăng nhập), không khớp authorization sau khi refresh. Thu hồi theo người dùng thì đăng xuất ở một trình duyệt sẽ đá mọi trình duyệt khác.
- Không có grace: an toàn hơn trên giấy, nhưng refresh song song của chính gateway sẽ đăng xuất người dùng thật.

**Bằng chứng.**
- [`LogoutRevocationTest`](../../../platform/api-gateway/src/test/java/com/htv/farmverse/gateway/LogoutRevocationTest.java) (gateway, identity giả): đăng xuất gửi đúng refresh token kèm client auth; identity lỗi vẫn đăng xuất; người chưa đăng nhập thì không gọi identity. Đối chứng: tắt handler thì 2/3 test đỏ.
- [`RefreshTokenReuseTest`](../../../services/identity-service/src/test/java/com/htv/farmverse/identity/RefreshTokenReuseTest.java): dùng lại token cũ làm token mới mất hiệu lực; lần đăng nhập khác không bị ảnh hưởng; log không có token.
- [`TokenLifecycleTest`](../../../services/identity-service/src/test/java/com/htv/farmverse/identity/TokenLifecycleTest.java): trong khoảng grace, token cũ bị từ chối còn token mới vẫn chạy.
- [`AccountLockoutTest`](../../../services/identity-service/src/test/java/com/htv/farmverse/identity/AccountLockoutTest.java): đúng 5 lần sai mới khóa; đăng nhập đúng thì đếm lại từ đầu; quản trị viên mở khóa được. Trước khi sửa, test thứ ba đỏ.
- `PostgresFlowsIT` chạy lại hai test identity trên PostgreSQL 17 thật (V4, vai trò `identity_app`).

**Chưa chứng minh.**
- Hai lần refresh song song thật của gateway (Redis, nhiều request). Grace 30 giây là ước lượng.
- Access token đã cấp vẫn dùng được tới khi hết hạn, tối đa 5 phút. Muốn cắt ngay thì cần introspection hoặc danh sách chặn, chưa làm.

**Rollback.** Bỏ handler trong `SecurityConfiguration#security` của gateway. Trả bean `authorizationService` của identity về `JdbcOAuth2AuthorizationService`; bảng V4 để nguyên cũng không hại gì. Đặt `farmverse.identity.refresh-token-reuse-grace` rất lớn thì tắt việc phát hiện mà không cần đổi code. Việc sửa đếm hai lần không nên rollback.
