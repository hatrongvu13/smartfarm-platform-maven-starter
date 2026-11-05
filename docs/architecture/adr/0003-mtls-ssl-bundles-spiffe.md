# ADR-0003: mTLS bằng Spring Boot SSL bundles + SAN SPIFFE

**Trạng thái:** Đã chốt.

**Bối cảnh.** Cần chứng minh danh tính máy giữa các service mà không thêm service mesh.

**Quyết định.** Mỗi service có cert do CA FarmVerse ký, SAN `spiffe://farmverse/<service>`. Server bật `client-auth` (`need` ở farm, `want` ở identity vì trình duyệt vào trang login). `ServiceIdentityFilter` kiểm SAN theo allowlist. Cert nạp qua SSL bundle PEM, `reload-on-update`.

**Phân biệt ba lớp** (không lớp nào thay được lớp khác):

| Lớp | Chứng minh | Không chứng minh |
|---|---|---|
| mTLS | Máy gọi là service nào | Người dùng là ai, được làm gì |
| JWT/JWKS | Token do identity cấp, còn hạn, đúng người nhận; người dùng và tenant | Request đến từ máy nào |
| Authorization | Người dùng được làm hành động này trên dữ liệu này | Token có thật không |

**Hệ quả.** Prod bắt buộc (`prod` kéo theo `mtls`). Dev mặc định tắt cho dễ chạy; ba service phải cùng bật hoặc cùng tắt.

**Bằng chứng.** [`ServiceIdentityFilter`](../../../libs/farmverse-starter-service/src/main/java/com/htv/farmverse/service/mtls/ServiceIdentityFilter.java), [`MtlsClients`](../../../libs/farmverse-starter-service/src/main/java/com/htv/farmverse/service/mtls/MtlsClients.java), `MtlsIntegrationTest` ở farm và identity, [`docs/code-walkthrough.md` mục 7.4](../../code-walkthrough.md#74-mtls-giữa-service-bằng-ssl-bundle).
