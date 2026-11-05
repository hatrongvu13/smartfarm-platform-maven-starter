# ADR-0002: Spring Authorization Server làm OIDC provider

**Trạng thái:** Đã chốt.

**Bối cảnh.** Dự án là môi trường học Spring. Đã từng có hai hệ token song song (SAS + tự viết), gây nhầm lẫn và lỗ hổng.

**Quyết định.** Một hệ token duy nhất: SAS. Không Keycloak. Đăng nhập form tenant-aware, TOTP là bước thứ hai tự viết nhỏ (Spring Security 7.1.2 chưa có factor TOTP). Quyền (`perms`, `roles`) và `tenant_id` nhúng vào access token lúc cấp.

**Hệ quả.**
- Phải tự lo những thứ IdP thương mại có sẵn: giới hạn đăng nhập, xoay khóa ký, audit.
- Quyền nhúng trong token: đổi quyền có hiệu lực ở token kế tiếp (tối đa 5 phút).
- TOTP tự viết phải giữ test chống replay và mã hóa secret.

**Bằng chứng.** [`OAuthClientProvisioner`](../../../services/identity-service/src/main/java/com/htv/farmverse/identity/oauth2/OAuthClientProvisioner.java), [`FarmVerseTokenCustomizer`](../../../services/identity-service/src/main/java/com/htv/farmverse/identity/oauth2/FarmVerseTokenCustomizer.java), [`TotpService`](../../../services/identity-service/src/main/java/com/htv/farmverse/identity/mfa/TotpService.java), [`docs/code-walkthrough.md` mục 7.2](../../code-walkthrough.md#72-oidc-với-spring-authorization-server-quyền-nằm-trong-jwt).
