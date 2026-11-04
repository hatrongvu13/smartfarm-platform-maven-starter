# ADR-0012: Trace, metric và log JSON không chứa bí mật

**Trạng thái:** Đã chốt (M7, 2026-11-04).

**Bối cảnh.** Một request đi qua gateway → farm hoặc identity, nhưng log của ba service không nối được với nhau, không có metric để scrape, và chỉ identity ở prod có log JSON (P2-5, T17). Cũng chưa có test nào chứng minh token, cookie hay mã OAuth2 không lọt vào log (T16).

**Phương án.**

| Phương án | Ưu | Nhược |
|---|---|---|
| A. Tự sinh request id (header `X-Request-Id`) và ghi vào MDC | Không thêm thư viện | Không theo chuẩn, không có span và thời gian từng chặng, phải tự lan qua mọi client |
| B. `spring-boot-starter-opentelemetry` | Một dependency | Kéo thêm `micrometer-registry-otlp`, registry này mặc định đẩy metric tới `localhost:4318` dù không có collector |
| C. Micrometer Tracing + bridge OpenTelemetry + exporter OTLP; metric Prometheus | Chuẩn W3C, Boot tự cấu hình, SCG tự lan `traceparent`; chỉ xuất trace khi có endpoint | 4 dependency mỗi service |

**Quyết định.** C.
- **Trace.** Dùng `spring-boot-micrometer-tracing-opentelemetry`, `micrometer-tracing-bridge-otel` và `opentelemetry-exporter-otlp`. Lan truyền theo W3C (`traceparent`). Mặc định lấy mẫu 10% (`TRACING_SAMPLING_PROBABILITY`); dev lấy 100%. Exporter OTLP chỉ được tạo khi đặt `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT`. **Không** khai khóa này trong yml, kể cả với giá trị rỗng: `@ConditionalOnProperty` coi chuỗi rỗng là đã đặt.
- **Baggage tắt** ở cả ba service, nên header `baggage` mà client gửi không được đọc vào context hay MDC.
- **Gateway** đặt `spring.reactor.context-propagation: auto` để trace có trong MDC trên các luồng Reactor. Thiếu dòng này, log của gateway có cột trace rỗng.
- **Metric.** Dùng `micrometer-registry-prometheus`, chỉ mở ở cổng quản trị loopback. Gateway luôn có cổng quản trị; farm và identity có ở profile `mtls`, mà prod luôn bật profile này. Ở profile mặc định, actuator của farm và identity dùng chung cổng ứng dụng, nên không mở `prometheus`. Mọi metric có thẻ `application`.
- **Log JSON** (ECS) ở prod cho cả ba service; mỗi dòng có `traceId` và `spanId`.
- **Cookie không đi xuống service.** Thêm default filter `RemoveRequestHeader=Cookie`. `FV_SESSION` và `XSRF-TOKEN` chỉ dành cho gateway: service xác thực bằng token (TokenRelay), còn SPA là file tĩnh.

**Phát hiện khi viết test** (bật DEBUG cho cả package, rồi tìm bí mật trong log):

| Logger (mức DEBUG) | In ra | Xử lý |
|---|---|---|
| `ObservedRequestHttpHeadersFilter` (gateway) | Mọi header: `Cookie`, `Authorization`, `X-XSRF-TOKEN` | Ghim cả package `...gateway.filter.headers.observation` ở INFO |
| `HttpWebHandlerAdapter` (gateway) | URL kèm query, gồm `?code=` của callback | Ghim ở INFO |
| `DefaultRedirectStrategy` (identity) | URL redirect về gateway, gồm `?code=` | Ghim ở INFO |
| Gateway chuyển tiếp nguyên `Cookie` | `FV_SESSION` tới identity, farm và web | `RemoveRequestHeader=Cookie` |

Cấu hình cho một logger cụ thể thắng cấu hình cho package của nó. Vì vậy bật `logging.level.org.springframework.security=DEBUG` vẫn an toàn. Chỉ khi ai đó đặt DEBUG cho đúng tên logger đã ghim thì bí mật mới lại bị in ra.

**Bằng chứng.**
- [`GatewayObservabilityTest`](../../../platform/api-gateway/src/test/java/com/htv/farmverse/gateway/GatewayObservabilityTest.java): trace id đến được nối tiếp xuống service; không có trace đến thì mở trace mới; log mang trace id; `/actuator/prometheus` chỉ có ở cổng quản trị; ở DEBUG không có cookie, token, CSRF hay mã OAuth2 trong log; service phía sau không nhận cookie.
- [`FarmObservabilityTest`](../../../services/farm-service/src/test/java/com/htv/farmverse/farm/FarmObservabilityTest.java): trace id từ gateway có trong log; token không có; không có prometheus ở cổng ứng dụng.
- [`IdentityObservabilityTest`](../../../services/identity-service/src/test/java/com/htv/farmverse/identity/IdentityObservabilityTest.java): chạy trọn luồng login → authorize → token → `/me` ở DEBUG; không có mật khẩu, client secret, mã, `code_verifier`, access token hay refresh token trong log.
- Đã chạy jar gateway thật với collector OTLP giả: span được gửi tới `/v1/traces` (protobuf) với trace id của request; không đặt endpoint thì không gửi gì; log JSON có `traceId` và `spanId`.

**Chưa chứng minh.**
- Collector thật (OpenTelemetry Collector, Tempo, Jaeger) và trace nối qua mTLS ở môi trường thật.
- Prometheus thật scrape qua cổng 9090 trong container.
- Không có alert hay dashboard.
- Trace id chưa nằm trong ProblemDetail trả cho người dùng. Thêm vào là đổi schema API, phải duyệt riêng.

**Rủi ro còn lại.**
- `traceparent` do client gửi được tin. Client có thể chọn trace id để log của mình gom vào một trace. Không lộ dữ liệu và không đổi quyền.
- Header `baggage` vẫn đi xuống service như mọi header khác. Service không đọc nó vì baggage đã tắt.

**Rollback.** Xóa 4 dependency trong ba `pom.xml`, khối `management.tracing`/`metrics`, `spring.reactor.context-propagation` và `logging.structured` ở prod của gateway và farm. Nên **giữ** phần ghim logger và `RemoveRequestHeader=Cookie`, vì chúng độc lập với tracing.
