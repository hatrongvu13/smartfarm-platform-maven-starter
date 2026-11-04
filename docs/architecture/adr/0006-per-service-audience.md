# ADR-0006: Audience riêng cho từng resource server

**Trạng thái:** Đã chốt, bước 1 (M6, 2026-11-04). Bước 2 (token riêng cho từng route) chưa làm.

**Bối cảnh.** Trước M6, mọi access token có `aud=farmverse-api`, và farm không kiểm `scope`. Một service đọc được token (qua log, hoặc vì bị chiếm) là dùng được token đó ở mọi service khác. Một token chỉ có `openid` nhưng mang `perms` vẫn vào được farm.

**Phương án.**

| Phương án | Cách làm | Ưu | Nhược |
|---|---|---|---|
| A. Mỗi service một audience, token mang danh sách | `aud=[farm-service, identity-service]`; mỗi service chỉ nhận audience của mình | Gateway không đổi (một token cho mọi route). Thêm service mà không thêm nó vào danh sách thì service đó không nhận token | Token vẫn dùng chéo được giữa các service **trong** danh sách |
| B. Resource indicator (RFC 8707) | Gateway xin token theo `resource` từng route | Đúng chuẩn, token hẹp nhất | Gateway phải giữ nhiều token; cần kiểm SAS 7.1 hỗ trợ tới đâu |
| C. Token exchange (RFC 8693) | Gateway đổi token người dùng lấy token `aud=farm-service` | Chặt nhất | Thêm lời gọi identity, cần cache |

**Quyết định.** A, làm ngay. B hoặc C để sau, khi có service thứ ba và có lý do đo được.
- Identity cấp `aud` theo `farmverse.identity.token-audiences`, mặc định `farm-service,identity-service` ([`FarmVerseTokenCustomizer`](../../../services/identity-service/src/main/java/com/htv/farmverse/identity/oauth2/FarmVerseTokenCustomizer.java)).
- Farm nhận `${FARM_ACCEPTED_AUDIENCES:farm-service}`. REST quản trị của identity nhận `farmverse.identity.accepted-audiences`, mặc định `identity-service`. Cả hai dùng [`FarmVerseJwtValidators.issuerAndAudience(issuer, danh sách)`](../../../libs/farmverse-starter-service/src/main/java/com/htv/farmverse/service/security/FarmVerseJwtValidators.java): token hợp lệ khi `aud` chứa ít nhất một giá trị được nhận.
- Farm đòi `SCOPE_farmverse.api` cho mọi request ([`SecurityConfiguration`](../../../services/farm-service/src/main/java/com/htv/farmverse/farm/config/SecurityConfiguration.java)), rồi `@PreAuthorize` mới kiểm `perms`.

**Thứ tự triển khai (không gián đoạn).** Access token sống 5 phút.
1. Farm: `FARM_ACCEPTED_AUDIENCES=farm-service,farmverse-api`. Identity: `farmverse.identity.accepted-audiences=identity-service,farmverse-api`. Bước này nhận cả token cũ lẫn mới.
2. Triển khai identity mới (cấp `aud` mới).
3. Sau ít nhất 5 phút, bỏ `farmverse-api` ở cả hai nơi (về mặc định).

**Rollback.** Đặt `farmverse.identity.token-audiences=farmverse-api`, đồng thời cho farm và identity nhận `farmverse-api`.

**Bằng chứng.**
- [`TokenValidationTest`](../../../services/farm-service/src/test/java/com/htv/farmverse/farm/TokenValidationTest.java) dùng token ký thật qua bộ giải mã thật của Boot:
  - `aud` sai, audience cũ, issuer lạ, hết hạn, `nbf` ở tương lai, chữ ký giả → 401.
  - Thiếu scope → 403.
  - JWKS được cache; gặp `kid` mới thì tải lại một lần.
  - Đối chứng: nới `FARM_ACCEPTED_AUDIENCES` thì test đỏ.
- [`AudienceTest`](../../../services/identity-service/src/test/java/com/htv/farmverse/identity/AudienceTest.java): token chỉ cho farm bị REST quản trị của identity từ chối.
- [`FarmVerseJwtValidatorsTest`](../../../libs/farmverse-starter-service/src/test/java/com/htv/farmverse/service/security/FarmVerseJwtValidatorsTest.java).
