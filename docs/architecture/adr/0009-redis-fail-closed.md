# ADR-0009: Redis lỗi thì gateway fail-closed với 503

**Trạng thái:** Đã chốt (M3, 2026-11-04).

**Bối cảnh.** Session BFF (chứa access/refresh token) nằm ở Redis. Redis lỗi thì gateway không đọc được phiên. Trước M3: request trả 500 chung, không timeout riêng (Lettuce mặc định 60 giây), TTL và namespace để mặc định.

**Phương án.**

| Phương án | Ưu | Nhược |
|---|---|---|
| A. Fail-open: coi như chưa đăng nhập, cho tạo phiên mới | Người dùng vẫn vào được trang công khai | Mọi người bị đẩy đi đăng nhập lại; dễ nhầm với lỗi xác thực |
| B. Fail-closed 503 có `code` | Không bao giờ cho request thiếu token đi tiếp; SPA biết đây là lỗi tạm | Redis sập = cả hệ thống 503 |
| C. Phiên dự phòng trong bộ nhớ | Còn chạy khi Redis sập | Phiên lệch giữa các instance; token nằm rải rác; trái ADR-0001 |

**Quyết định.** B. `RedisUnavailableHandler` (`WebExceptionHandler`, order -2) đổi lỗi Redis (`DataAccessResourceFailureException`, `QueryTimeoutException`, `RedisSystemException` ở bất kỳ tầng `cause` nào) thành 503 `application/problem+json`, `code=SF-GATEWAY-503`, `Retry-After: 5`, `Cache-Control: no-store`. Lỗi khác đi tiếp như cũ. Timeout lệnh 2 giây, kết nối 1 giây; phiên 30 phút; namespace `farmverse:gateway:session`.

**Hệ quả.**
- Log chỉ ghi loại lỗi và đường dẫn, không ghi cookie, header hay thông điệp lỗi.
- `RequestRateLimiter` của Spring Cloud Gateway tự cho qua khi Redis lỗi (đã đọc bytecode `RedisRateLimiter` 5.0.4). Chấp nhận được vì `/api/**` đòi phiên, và phiên cần Redis.
- Request không có cookie phiên không đọc Redis, nên vẫn nhận 401 như thường.

**Bằng chứng.** [`RedisUnavailableHandler`](../../../platform/api-gateway/src/main/java/com/htv/farmverse/gateway/config/RedisUnavailableHandler.java), [`RedisSessionHardeningTest`](../../../platform/api-gateway/src/test/java/com/htv/farmverse/gateway/RedisSessionHardeningTest.java) (đỏ trước khi sửa: 500 thay vì 503), [`RedisUnavailableHandlerTest`](../../../platform/api-gateway/src/test/java/com/htv/farmverse/gateway/config/RedisUnavailableHandlerTest.java).

**Chưa chứng minh.** Redis treo (nhận kết nối nhưng không trả lời) và Redis có ACL thật: cần `*IT` với Testcontainers. Test hiện tại dùng cổng từ chối kết nối.

**Rollback.** Xóa `RedisUnavailableHandler`; revert khối `spring.data.redis` / `spring.session` trong `application.yml`.
