# ADR-0007: Test gateway dùng kho session trong bộ nhớ

**Trạng thái:** Đã chốt (M2a, 2026-11-04).

**Bối cảnh.** Hai test logout của `GatewaySecurityTest` mở WebSession. Spring Session Redis là kho mặc định, nên test cần Redis đang chạy ở `127.0.0.1:6379`. Không có Redis thì build đỏ (`RedisConnectionFailureException`). Baseline 2026-11-04 đỏ đúng vì lý do này. CI sau này cũng sẽ đỏ hoặc phải dựng Redis cho mọi lần build.

**Phương án.**

| Phương án | Ưu | Nhược |
|---|---|---|
| A. Bean `ReactiveMapSessionRepository` chỉ trong test | Không đụng code chính; nhanh; Boot tự lùi (`@ConditionalOnMissingBean(ReactiveSessionRepository)`) | Không kiểm hành vi riêng của Redis |
| B. Testcontainers Redis cho mọi test | Gần production | Cần Docker ở mọi máy; chậm (lúc chốt, máy dev chưa có Docker; nay Redis thật chạy ở `GatewayRedisIT`) |
| C. Tắt auto-config Redis bằng thuộc tính | Ngắn | Phải liệt kê nhiều auto-config; dễ gãy khi nâng Boot |

**Quyết định.** A cho test thường. Hành vi Redis thật (TTL, mất kết nối, fail-closed) để cho test tích hợp `*IT` với Testcontainers ở M3, chạy trong CI.

**Hệ quả.**
- `mvn test` chạy được trên máy không có Redis.
- Test `sessionStoreInTestsIsInMemory` khẳng định kho đúng loại; nếu Boot đổi điều kiện lùi, test báo ngay thay vì âm thầm dùng Redis.
- Code production không đổi.

**Bằng chứng.** [`InMemorySessionTestConfiguration`](../../../platform/api-gateway/src/test/java/com/htv/farmverse/gateway/InMemorySessionTestConfiguration.java), [`GatewaySecurityTest`](../../../platform/api-gateway/src/test/java/com/htv/farmverse/gateway/GatewaySecurityTest.java). Đối chứng: bỏ `@Import` thì 2 test đỏ với `REDIS_PORT=1`.

**Rollback.** Xóa lớp cấu hình test và dòng `@Import`.
