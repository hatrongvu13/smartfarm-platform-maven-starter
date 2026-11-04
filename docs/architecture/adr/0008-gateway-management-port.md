# ADR-0008: Actuator của gateway trên cổng quản trị loopback

**Trạng thái:** Đã chốt (M2, 2026-11-04).

**Bối cảnh.** Gateway mở `/actuator`, `/actuator/info`, `/actuator/health` cho người lạ trên cổng công khai 8080 (luật `anyExchange().permitAll()`). Cấu hình còn ghi `gateway` trong exposure: endpoint đó không bật (đã kiểm), nhưng dòng cấu hình gây hiểu nhầm và chỉ cần một thay đổi nhỏ là lộ danh sách route kèm URI nội bộ. Identity và farm đã đặt actuator ở cổng quản trị loopback; gateway thì chưa.

**Phương án.**

| Phương án | Ưu | Nhược |
|---|---|---|
| A. Thêm luật `denyAll` cho `/actuator/**` trừ health | Ít đổi | Phụ thuộc thứ tự luật bảo mật; vẫn chung cổng công khai |
| B. Cổng quản trị riêng, bind `127.0.0.1`, exposure `health,info` | Giống identity/farm; tách ở tầng mạng, đúng ngay cả khi luật bảo mật sai | Probe phải gọi cổng quản trị |

**Quyết định.** B. `management.server.port: ${GATEWAY_MANAGEMENT_PORT:9080}`, `address: 127.0.0.1`, exposure `health,info`. Trong container, `Dockerfile` đặt `MANAGEMENT_SERVER_PORT=9090` cho cả ba service để healthcheck dùng một cổng chung.

**Chi tiết dễ sai.** Spring Cloud Gateway không định tuyến trên cổng quản trị bằng cách so cổng thật của request với **số** trong `management.server.port` (`RoutePredicateHandlerMapping#getHandlerInternal`, bản 5.0.4). Với `0` (cổng ngẫu nhiên) hai số không khớp, nên route `web` chạy cả trên cổng quản trị. Production dùng cổng cố định nên không bị. Test dùng một cổng trống cố định vì lý do này.

**Hệ quả.**
- Cổng 8080 không còn `/actuator/**` (request rơi vào route `web`).
- Kubernetes: probe `httpGet` không tới được cổng loopback; dùng probe `exec` hoặc đổi `management.server.address` sang IP pod khi triển khai (chưa có manifest).

**Bằng chứng.** [`application.yml`](../../../platform/api-gateway/src/main/resources/application.yml), [`GatewayActuatorExposureTest`](../../../platform/api-gateway/src/test/java/com/htv/farmverse/gateway/GatewayActuatorExposureTest.java) (đỏ trước khi sửa, xanh sau), [`Dockerfile`](../../../Dockerfile).

**Rollback.** Revert khối `management` trong `application.yml` và hai biến cổng trong `Dockerfile`.
