# ADR-0001: BFF giữ token phía server

**Trạng thái:** Đã chốt.

**Bối cảnh.** SPA giữ access/refresh token trong `localStorage` thì XSS đọc được token. Refresh token sống 14 ngày.

**Quyết định.** Gateway là OAuth2 Client (confidential). Token nằm trong WebSession lưu ở Redis. Trình duyệt chỉ có cookie opaque `FV_SESSION` (`HttpOnly`, `SameSite=Lax`, `Path=/`, `Secure` ở prod). Gateway gắn Bearer khi chuyển tiếp bằng `TokenRelay`. CSRF bảo vệ bằng cookie + header.

**Hệ quả.**
- XSS không lấy được token, nhưng vẫn gửi request thay người dùng trong phiên: CSP vẫn cần (M5).
- Redis thành tài sản nhạy cảm: phải có mật khẩu/ACL, TLS, timeout (M3).
- Gateway có trạng thái; scale ngang nhờ session chung ở Redis.

**Bằng chứng.** [`SecurityConfiguration`](../../../platform/api-gateway/src/main/java/com/htv/farmverse/gateway/config/SecurityConfiguration.java), [`BffController`](../../../platform/api-gateway/src/main/java/com/htv/farmverse/gateway/bff/BffController.java), [`docs/code-walkthrough.md` mục 7.1](../../code-walkthrough.md#71-bff--token-relay-gateway).
