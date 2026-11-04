# ADR-0011: Chỉ tin header forwarded từ edge đã khai

**Trạng thái:** Đã chốt (M5c, 2026-11-04).

**Bối cảnh.** Ở prod, TLS kết thúc ở edge; gateway nhận `http://<máy nội bộ>`. Spring OAuth2 Client dựng `redirect_uri` từ URL của request ở **hai** chỗ: lúc bắt đầu (`{baseUrl}/login/oauth2/code/{registrationId}`) và lúc callback (`ServerOAuth2AuthorizationCodeAuthenticationTokenConverter` đọc `request.getURI()`), rồi so hai giá trị. Identity chỉ chấp nhận `${GATEWAY_PUBLIC_URL}/login/oauth2/code/farmverse` (`https://`). Kết quả: đăng nhập sau edge hỏng. Đã tái hiện bằng test: `redirect_uri` = `http://127.0.0.1:<port>/...`.

**Phương án.**

| Phương án | Ưu | Nhược |
|---|---|---|
| A. Cấu hình cứng URL công khai cho `redirect_uri` | Không tin header nào | Callback vẫn dựng URL từ request (`http://`), nên lệch ở bước so; đăng nhập vẫn hỏng |
| B. `server.forward-headers-strategy: native` hoặc `framework` | Một dòng | Tin `X-Forwarded-*` từ **mọi** nguồn; WebFlux không có danh sách proxy tin cậy |
| C. Bộ lọc forwarded tự viết có danh sách IP/CIDR tin cậy | Chỉ edge đổi được scheme/host; nguồn khác bị xóa header | Thêm một lớp nhỏ phải giữ |

**Quyết định.** C. `TrustedProxyForwardedHeaderTransformer` kế thừa `ForwardedHeaderTransformer` của Spring và được đăng ký làm bean `forwardedHeaderTransformer` (tên mà `WebHttpHandlerBuilder` tìm), nên nó chạy trước mọi `WebFilter`. Nếu kết nối TCP đến từ `GATEWAY_TRUSTED_PROXIES` thì áp dụng header. Nếu không thì xóa (`removeOnly`). Bỏ qua `X-Forwarded-Prefix`. Đặt `server.forward-headers-strategy: none`, vì Boot tự bật `native` khi thấy Kubernetes. Profile prod mà danh sách rỗng thì dừng ngay khi khởi động.

**Chi tiết dễ sai** (đều đã bị test bắt trong lúc làm):
- `ForwardedHeaderTransformer(boolean)`: tham số là `useStandardHeader`, **không phải** `removeOnly`. Phải gọi `setRemoveOnly(true)`.
- `IpAddressMatcher` của Spring Security cần Servlet API, mà gateway là WebFlux. Vì vậy dùng bộ so CIDR nhỏ có unit test riêng.

**Giả định.** Edge **ghi đè** `X-Forwarded-*` mà trình duyệt gửi. Nếu edge chỉ nối thêm, trình duyệt có thể chèn host giả qua edge. Identity vẫn chặn được nhờ chỉ nhận redirect URI đã đăng ký, nhưng URL khác do gateway dựng thì không được bảo vệ.

**Bằng chứng.** [`TrustedProxyForwardedHeaderTransformer`](../../../platform/api-gateway/src/main/java/com/htv/farmverse/gateway/config/TrustedProxyForwardedHeaderTransformer.java), [`ForwardedHeadersTest`](../../../platform/api-gateway/src/test/java/com/htv/farmverse/gateway/ForwardedHeadersTest.java) (đăng nhập trọn vẹn qua edge với identity giả; header giả từ nguồn không tin bị bỏ), [`TrustedProxyForwardedHeaderTransformerTest`](../../../platform/api-gateway/src/test/java/com/htv/farmverse/gateway/config/TrustedProxyForwardedHeaderTransformerTest.java).

**Chưa chứng minh.** Chạy với load balancer hoặc ingress thật.

**Rollback.** Xóa bean `forwardedHeaderTransformer`, xóa `server.forward-headers-strategy` và `farmverse.gateway.trusted-proxies`. Gateway quay lại không đọc header forwarded, và đăng nhập sau edge hỏng như trước.
