# ADR-0010: CSP theo profile và HSTS sau edge TLS

**Trạng thái:** Đã chốt (M5b, 2026-11-04).

**Bối cảnh.** Token không ở trình duyệt (ADR-0001), nhưng XSS vẫn gửi được request thay người dùng trong phiên. Gateway chưa có CSP, và HSTS mặc định của Spring không bao giờ được gửi ở prod. Lý do: bộ ghi `StrictTransportSecurityServerHttpHeadersWriter` chỉ ghi khi request là HTTPS, mà TLS lại kết thúc ở edge.

**Khảo sát SPA** (bản `vite build`): HTML chỉ có một `<script type="module" src="/assets/...">` và một `<link rel="stylesheet">`, đều cùng origin. Không có script hay style nhúng. Hai chỗ `.innerHTML` trong bundle là code nội bộ của React. Không tải gì từ origin khác. Vite **dev** thì khác: nó chèn script preamble React Refresh, chèn CSS bằng JS, và mở websocket HMR.

**Phương án.**

| Phương án | Ưu | Nhược |
|---|---|---|
| A. Một CSP nới (`'unsafe-inline'`) cho mọi môi trường | Không bao giờ vỡ dev | Prod mất gần hết tác dụng chống XSS |
| B. CSP chặt cho prod, dev ghi đè bằng profile | Prod chặt; dev chạy được | Hai chuỗi CSP phải giữ đồng bộ |
| C. CSP dùng nonce | Chặt kể cả khi cần script nhúng | Gateway phải sửa HTML của SPA; SPA hiện không cần |

**Quyết định.** B.
- `application.yml`: `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: blob:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self' {identity-origin}`.
- `{identity-origin}` là origin của issuer. Lý do: form đăng xuất (`POST /logout`) redirect sang `/connect/logout` của identity, và trình duyệt áp `form-action` cả cho redirect đó.
- `application-dev.yml`: thêm `'unsafe-inline'` cho script và style, thêm `ws://127.0.0.1:8080` và `ws://localhost:8080`. Không có `'unsafe-eval'`.
- HSTS: tắt bộ ghi của Spring. Khi `farmverse.gateway.headers.hsts=true` (prod), gateway tự ghi `max-age=31536000`. Không `includeSubDomains`, không `preload`, vì hai cờ đó ảnh hưởng tên miền khác và khó gỡ.
- Thêm `Referrer-Policy: strict-origin-when-cross-origin`, `Permissions-Policy: camera=(), microphone=(), geolocation=(), payment=()`. Giữ mặc định của Spring: nosniff, `X-Frame-Options: DENY`.

**Bằng chứng.**
- [`SecurityHeadersTest`](../../../platform/api-gateway/src/test/java/com/htv/farmverse/gateway/SecurityHeadersTest.java): trang SPA đi qua route `web` (server giả), lỗi 401, HSTS chỉ một lần, khóa cấu hình theo profile. Test đỏ trước khi sửa.
- Chrome headless thật (2026-11-04):
  - Bản build qua gateway với CSP prod: trang dựng xong, 0 lỗi CSP.
  - Đối chứng dương tính: trang có script nhúng qua gateway → bị chặn và ghi log; gọi thẳng không qua gateway → script chạy.
  - Vite dev qua gateway với CSP dev: trang dựng xong, 0 lỗi.

**Chưa chứng minh.**
- Cảnh 3D (WebGL), vì chạy với `--disable-gpu`.
- Các trang sau khi đăng nhập, vì identity không chạy trong bài thử.
- HMR thật, khi sửa file lúc đang chạy.

**Hệ quả.** Thêm CDN, font ngoài, `eval`, hay thư viện chèn `<style>` lúc chạy sẽ bị chặn ở prod. Khi đó phải sửa CSP có chủ đích.

**Rollback.** Revert lời gọi `.headers(...)` trong `SecurityConfiguration` và khối `farmverse.gateway.headers` trong ba file yml.
