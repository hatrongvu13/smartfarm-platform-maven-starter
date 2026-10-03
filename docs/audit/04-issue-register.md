# 04 — Issue Register (Sổ đăng ký vấn đề)

> Phạm vi: `order-service`, `identity-service`, `libs/smartfarm-security`, event-flow toàn nền tảng.
> Mọi khẳng định đều gắn nhãn **FACT / INFERENCE / UNKNOWN / RISK / BLOCKER**. Không chỉnh sửa source — chỉ MÔ TẢ patch.
> Dòng, file, symbol được xác nhận bằng cách đọc source thật; node/edge tham chiếu graph `graphify-out/graph.json`.

Tổng hợp mức độ: **CRITICAL 3 · HIGH 6 · MEDIUM 7 · LOW 3 · INFO 2** (tổng 21).

---

## ISSUE-01: `order-service` không khởi động được ở profile `prod` do sai cấu trúc key cấu hình security

- **Severity**: CRITICAL
- **Category**: CONFIGURATION
- **Confidence**: HIGH
- **Status**: OPEN
- **Module**: `smartfarm-order-service` + `libs/smartfarm-security`
- **Business capability**: Toàn bộ nghiệp vụ đặt hàng (order placement/saga/outbox) — service là owner của saga.
- **Affected files**:
  - `services/smartfarm-order-service/src/main/resources/application.yml` (L39–L43)
  - `services/smartfarm-order-service/src/main/resources/application-prod.yml` (khối `smartfarm.security`)
  - `libs/smartfarm-security/src/main/java/com/htv/smartfarm/security/config/SmartFarmSecurityProperties.java`
- **Affected symbols**: `SmartFarmSecurityProperties` (compact constructor), `SmartFarmSecurityProperties.Jwt`
- **Graph nodes**: `SmartFarmSecurityProperties`, `SmartFarmSecurityPropertiesAutoConfiguration`
- **Graph edges**: `SmartFarmSecurityPropertiesAutoConfiguration --references--> SmartFarmSecurityProperties`
- **Evidence** (FACT):
  - `SmartFarmSecurityProperties` dùng `@ConfigurationProperties(prefix = "smartfarm.security")` với các thành phần lồng `jwt`, `grpc`, `correlation`; `Jwt` có `issuer`, `jwkSetUri`, `audiences` (Set, plural). Compact constructor: `if (jwt == null) throw new IllegalArgumentException("smartfarm.security.jwt is required");`.
  - `application.yml` (order) viết **phẳng, số ít**: `smartfarm.security.issuer / jwk-set-uri / audience`.
  - `application-prod.yml` (order) viết **phẳng, số ít** y hệt; `grep "jwt"` trong file trả về rỗng → **không có** khối `smartfarm.security.jwt`.
  - Đối chiếu: `application-dev.yml` (order), gateway (`application.yml/dev/prod`), identity (`application.yml/dev/prod`) đều viết đúng dạng lồng `smartfarm.security.jwt.{issuer,jwk-set-uri,audiences}`.
  - `order-service/pom.xml` phụ thuộc `smartfarm-security` → auto-config `SmartFarmSecurityPropertiesAutoConfiguration` được kích hoạt.
- **Current behavior** (INFERENCE từ FACT): Ở profile `prod`, không có `smartfarm.security.jwt.*` ⇒ Spring bind `jwt = null` ⇒ compact constructor ném `IllegalArgumentException` ⇒ context khởi tạo thất bại ⇒ **service không boot**. Ở `dev` chạy được vì `application-dev.yml` mang dạng lồng đúng.
- **Expected behavior**: `order-service` boot ở `prod`; `JwtDecoder`/`SmartFarmJwtValidator` nhận đúng issuer/jwks/audiences.
- **Root cause**: `application.yml` + `application-prod.yml` của order giữ lại schema key cũ (phẳng/số ít) không khớp record hiện tại.
- **Technical impact**: Core service không khởi động ở prod; gRPC order toàn bộ không phục vụ.
- **Business impact**: Không thể đặt/huỷ đơn trong môi trường production.
- **Security impact**: Nếu một biến thể cấu hình khiến `grpc.enabled=false` để "chạy được", interceptor JWT có thể không gắn → bypass auth (RISK).
- **Runtime risk**: Fail-fast lúc startup (tốt hơn fail-open), nhưng là outage.
- **Recommended solution** (DESCRIBED): Sửa `application.yml` và `application-prod.yml` của order sang dạng lồng:
  ```yaml
  smartfarm:
    security:
      jwt:
        issuer: ${SMARTFARM_JWT_ISSUER}
        jwk-set-uri: ${SMARTFARM_JWKS_URI}
        audiences:
          - smartfarm-order
      allow-local-http: false
  ```
- **Alternative solution**: Thêm relaxed-binding/alias trong record, hoặc dùng `@ConfigurationPropertiesBinding` converter nhận cả `audience` số ít. Kém minh bạch hơn → không khuyến nghị.
- **Compatibility considerations**: Chỉ đổi YAML, backward-compatible với các service khác (đã đúng dạng). Env var giữ nguyên.
- **Files to modify**: 2 file YAML order.
- **Suggested patch (DESCRIBED, not applied)**: Thay 3 dòng phẳng bằng khối `jwt:` lồng + `audiences:` list như trên; xoá key `audience` số ít.
- **Data migration required**: Không.
- **Configuration changes**: Có (YAML order).
- **Rollback plan**: Revert 2 file YAML.
- **Verification steps**: `SPRING_PROFILES_ACTIVE=prod` + env đầy đủ → service boot; actuator `/health` readiness = UP; thử 1 gRPC `GetOrder` không token → `UNAUTHENTICATED`.
- **Remaining risks**: UNKNOWN — có thể có cơ chế config bên ngoài (ConfigMap k8s `deploy/kubernetes/order-gateway.yaml`) ghi đè; cần xác nhận trước khi vá.
- **Dependencies**: Độc lập; nền tảng cho mọi test prod khác.

---

## ISSUE-02: MQTT event-bus KHÔNG có xác thực/toàn vẹn message — `MqttSecurityVerifier`/`MqttSecurityEnvelope` là class rỗng

- **Severity**: CRITICAL
- **Category**: SECURITY
- **Confidence**: HIGH
- **Status**: PARTIALLY_IMPLEMENTED (2026-10-02)
  - ĐÃ LÀM: triển khai `MqttSecurityEnvelope` (frame ký `SFM1`, HMAC-SHA256, ràng buộc topic) + `MqttSecurityVerifier` (sign/verify, constant-time, backward-compatible, mặc định TẮT) + `MqttSecurityProperties` trong `libs/smartfarm-security`; 8 unit test pass. ACL + mTLS viết thành cấu hình mẫu tại `docs/security/mqtt-acl-mtls.md` (CHƯA bật).
  - CÒN LẠI (OPEN): nối `verifier.sign/verify` vào publisher/consumer (patch mô tả trong doc); áp `allow_anonymous false` + ACL + TLS cho broker thật; sinh HMAC secret + cert; bật `sign-enabled` rồi `verify-enabled` theo thứ tự rollout.
- **Module**: `libs/smartfarm-security` (mqtt), toàn bộ consumer MQTT (gateway, inventory, order read-model, identity command)
- **Business capability**: Toàn bộ event-flow bất đồng bộ (order-changed, task-changed, identity command/result).
- **Affected files**:
  - `libs/smartfarm-security/src/main/java/com/htv/smartfarm/security/mqtt/MqttSecurityVerifier.java` (toàn file)
  - `libs/smartfarm-security/src/main/java/com/htv/smartfarm/security/mqtt/MqttSecurityEnvelope.java` (toàn file)
  - `services/smartfarm-order-service/.../readmodel/OrderChangedConsumer.java`
  - `apps/smartfarm-gateway/.../events/MqttEventSubscriber.java`
  - `services/smartfarm-inventory-service/.../mqtt/TaskMqttSubscriber.java` + `TaskInbox.java`
- **Affected symbols**: `MqttSecurityVerifier`, `MqttSecurityEnvelope`
- **Graph nodes**: `MqttSecurityVerifier`, `MqttSecurityEnvelope`
- **Graph edges**: không có cạnh `references`/`calls` tới 2 node (xác nhận bằng `grep` toàn repo).
- **Evidence** (FACT): Cả hai file chỉ chứa khai báo class rỗng (`public class MqttSecurityVerifier {}`). `grep -rn "MqttSecurityVerifier|MqttSecurityEnvelope"` ngoài chính 2 file → **NONE**. Các consumer parse thẳng payload protobuf/JSON và áp dụng (`projector.apply`, `inbox.accept`) mà không kiểm chữ ký/nguồn.
- **Current behavior**: Bất kỳ client nào có credential broker (hoặc broker mở) đều có thể publish event giả mạo đúng topic; consumer sẽ nhận và xử lý.
- **Expected behavior**: Event mang envelope ký (hoặc kênh được xác thực + ACL theo topic/tenant), consumer từ chối message không hợp lệ.
- **Root cause**: Thành phần bảo mật MQTT mới là stub, chưa triển khai; brief liệt kê như một thành phần an ninh nhưng không có logic.
- **Technical impact**: Tiêm event giả → sai read-model/CQRS, kích task kho ảo.
- **Business impact**: Có thể bịa trạng thái đơn, lệnh membership/role giả (identity command inbox có validate chặt hơn — xem ISSUE-09).
- **Security impact**: Thiếu authN/integrity ở event layer (RISK cao).
- **Runtime risk**: **CONFIRMED nghiêm trọng** — `infra/mosquitto.conf` đặt `allow_anonymous true`, KHÔNG ACL, KHÔNG TLS (listener 1883 + websockets 9001). Kết hợp verifier rỗng ⇒ **event bus mở hoàn toàn**: bất kỳ client ẩn danh nào cũng publish được event giả mạo đúng topic. Confidence nâng lên **CONFIRMED** cho cấu hình broker mặc định/dev.
- **Recommended solution** (DESCRIBED): Triển khai `MqttSecurityEnvelope` (metadata + chữ ký/HMAC hoặc JWS) và `MqttSecurityVerifier.verify(topic, payload)`; gọi tại từng consumer trước khi apply. Backward-compatible qua cờ `verify-enabled` mặc định off → bật dần.
- **Alternative solution**: Dựa hoàn toàn vào ACL broker + mTLS per-service (ít thay đổi code hơn), kết hợp xác thực tenant từ topic.
- **Compatibility considerations**: Thêm cờ bật/tắt để không gãy môi trường chưa ký.
- **Files to modify**: 2 class security + điểm gọi tại mỗi consumer.
- **Suggested patch (DESCRIBED)**: Định nghĩa envelope + verifier; chèn `if (verifyEnabled && !verifier.verify(...)) { drop; }`.
- **Data migration required**: Không.
- **Configuration changes**: Thêm key `smartfarm.security.mqtt.verify-enabled`, khóa ký.
- **Rollback plan**: Tắt cờ.
- **Verification steps**: Publish message giả → bị drop; message hợp lệ → apply.
- **Remaining risks**: UNKNOWN mức bảo vệ hiện tại của broker (`infra/mosquitto.conf`).
- **Dependencies**: Liên quan ISSUE-05 (độ bền consumer), ISSUE-06 (orphan event).

---

## ISSUE-03: Reverse-finance idempotency key không nhất quán giữa đường saga cũ (inline) và đường worker đang hoạt động

- **Severity**: CRITICAL
- **Category**: RELIABILITY
- **Confidence**: CONFIRMED
- **Status**: RESOLVED (2026-10-02 — dead path removed; drift only ever existed in dead code)
- **Module**: `smartfarm-order-service` (saga)
- **Business capability**: Bù trừ (compensation) khi đơn thất bại — đảo chi phí finance.
- **Affected files**:
  - `services/smartfarm-order-service/.../saga/OrderSagaOrchestrator.java` (method `compensate`, dòng khối `reverseExpense`)
  - `services/smartfarm-order-service/.../saga/worker/OrderSagaCompensationStepExecutor.java` (`reverseFinance`)
  - `services/smartfarm-order-service/.../saga/worker/OrderSagaForwardStepExecutor.java` (`finance`)
- **Affected symbols**: `OrderSagaOrchestrator.compensate`, `OrderSagaCompensationStepExecutor.reverseFinance`, `OrderSagaForwardStepExecutor.finance`
- **Graph nodes**: `OrderSagaOrchestrator`, `OrderSagaCompensationStepExecutor`, `OrderSagaForwardStepExecutor`
- **Graph edges**: `FarmOrderGrpcService --references--> OrderSagaOrchestrator`; worker executors `<-- OrderSagaPersistentWorker`.
- **Evidence** (FACT):
  - Đường **worker (đang chạy)**: forward finance dùng `context.idempotencyKey = step.getIdempotencyKey()` = `orderId + ":finance"` (step key = `"finance"`, graph tạo tại `OrderSagaTransactionService.graph`). `reverseFinance` set `originalExpenseKey = order.getId() + ":finance"` → **khớp**.
  - Đường **inline cũ** `OrderSagaOrchestrator`: forward dùng `ctx(order, actor, "expense")` → idempotency `orderId + ":expense"`; `compensate` set `originalExpenseKey = orderId + ":expense"` → nội bộ khớp, nhưng dùng **":expense"** thay vì **":finance"**.
  - `FarmOrderGrpcService.placeOrder` **không gọi** `saga.run(...)`; chỉ gọi `persistentSagas.create(...)` rồi worker đẩy. → `OrderSagaOrchestrator` hiện là code chết trong luồng chính (INFERENCE).
- **Current behavior**: Luồng active nhất quán. RỦI RO: nếu bất kỳ caller nào kích hoạt `OrderSagaOrchestrator.run/compensate` (test, admin, tương lai) song song với finance service đã nhận key `":finance"`, việc đảo theo key `":expense"` sẽ **không trùng idempotency** phía finance → có thể đảo nhầm/không đảo.
- **Expected behavior**: Chỉ một đường saga; key đảo khớp key gốc.
- **Root cause**: Tồn tại hai triển khai saga song song (inline + persistent) với quy ước key khác nhau.
- **Technical/Business impact**: Nếu đường cũ còn được dùng → đảo chi phí sai/trùng → lệch sổ finance.
- **Security impact**: Không.
- **Runtime risk**: Thấp hiện tại (đường cũ không được gọi), cao nếu tái dùng.
- **Recommended solution** (DESCRIBED): Gỡ bỏ `OrderSagaOrchestrator` khỏi luồng (deprecate), hoặc đồng bộ quy ước key `":finance"`. Xác nhận không còn caller.
- **Alternative**: Giữ nhưng chú thích rõ "dev-only" + chặn bean ở prod.
- **Files to modify**: `OrderSagaOrchestrator` (hoặc xoá bean injection trong `FarmOrderGrpcService`).
- **Suggested patch (DESCRIBED)**: Bỏ field `saga` khỏi `FarmOrderGrpcService` nếu đã xác nhận không dùng; hoặc đổi `"expense"`→`"finance"`.
- **Rollback**: Revert.
- **Verification**: Chạy `OrderSagaEntityHardeningTest` + test compensation; grep caller `saga.run`.
- **Remaining risks**: UNKNOWN — cần xác nhận test/admin nào còn gọi đường inline.
- **Dependencies**: ISSUE-08 (dual-path).

- **RESOLUTION (2026-10-02)**:
  - DELETED `services/smartfarm-order-service/.../saga/OrderSagaOrchestrator.java` (238 dòng, 0 caller — không `@Deprecated`, xoá hẳn để không tạo bean thừa + chặn tái dùng drift `:expense`).
  - MODIFIED `FarmOrderGrpcService.java`: gỡ import + field `saga` + ctor param + assignment. `new FarmOrderGrpcService(` = 0 match toàn repo → không test nào phải sửa.
  - Drift `:expense` **không còn tồn tại** trong source. Đường active giữ nguyên `orderId:finance` (forward `graph()` + `OrderSagaCompensationStepExecutor.reverseFinance` L59) — KHỚP.
  - VERIFY (Maven 3.9.16 / JDK17): `-pl services/smartfarm-order-service -am compile` EXIT=0; `test` BUILD SUCCESS (order 22, security 27, common 13 — 0 fail/err).
  - Không đụng proto / schema / MQTT topic / public gRPC / saga graph.
  - Tài liệu state machine + idempotency matrix: `docs/architecture/order-saga.md`.
  - ROLLBACK: `git revert` commit cleanup (chỉ 1 file xoá + 1 file sửa).

---

## ISSUE-04: Outbox relay dừng toàn bộ hàng đợi khi gặp 1 event lỗi topic (head-of-line blocking)

- **Severity**: HIGH
- **Category**: RELIABILITY
- **Confidence**: HIGH
- **Status**: RESOLVED (2026-10-03 — classified relay: dead+continue poison, break only on infra)
- **Module**: `smartfarm-order-service` (outbox) — mẫu lặp cả ở identity dispatcher.
- **Affected files**: `services/smartfarm-order-service/.../outbox/OrderOutboxRelay.java` (`poll`), `OrderEventMapper.topic` (validateSegment).
- **Affected symbols**: `OrderOutboxRelay.poll`, `OrderEventMapper.topic/validateSegment`
- **Graph nodes**: `OrderOutboxRelay`, `OrderEventMapper`, `OrderMqttPublisher`
- **Evidence** (FACT): `poll()` lặp batch; trong `catch (Exception)` gọi `markFailed` rồi **`break;`** — dừng xử lý các row còn lại của batch. `OrderEventMapper.topic` ném `IllegalArgumentException("Invalid MQTT topic segment")` nếu tenant/farm không khớp regex. Một event có segment lỗi sẽ throw → markFailed + break; vòng poll sau lại lấy đúng event lỗi đó trước (sắp theo `next_attempt_at, created_at`) → kẹt.
- **Current behavior**: 1 poison event có thể chặn toàn bộ publish cho tới khi nó bị `DEAD` (sau `maximum-attempts`, mặc định 12) — nhưng vẫn `break` mỗi vòng, trì hoãn các event hợp lệ.
- **Expected behavior**: Bỏ qua/`DEAD`-hoá poison ngay, tiếp tục event khác; chỉ `break` khi lỗi hạ tầng (broker down).
- **Root cause**: Không phân biệt lỗi hạ tầng (broker) với lỗi dữ liệu (topic invalid); `break` áp cho mọi exception.
- **Technical/Business impact**: Trễ/đình trệ phát sự kiện order → read-model & gateway WS lạc hậu.
- **Security impact**: Không.
- **Recommended solution** (DESCRIBED): Bắt riêng `IllegalArgumentException` (lỗi dữ liệu) → markDead ngay + `continue`; chỉ `MqttException` mới `break`.
- **Files to modify**: `OrderOutboxRelay`, (tùy chọn) thêm trạng thái DEAD cho lỗi dữ liệu.
- **Rollback**: Revert.
- **Verification**: Chèn 1 event tenant lỗi + nhiều event hợp lệ → event hợp lệ vẫn publish.
- **Remaining risks**: Thấp.
- **Dependencies**: ISSUE-02.

---

## ISSUE-05: `OrderChangedConsumer` dùng `cleanSession=false` nhưng `MemoryPersistence` — mất trạng thái QoS1 khi restart

- **Severity**: HIGH
- **Category**: RELIABILITY
- **Confidence**: HIGH
- **Status**: RESOLVED (2026-10-03 — Option A: FILE persistence + manual ACK after commit)
- **Module**: `smartfarm-order-service` (read-model)
- **Affected files**: `services/smartfarm-order-service/.../readmodel/OrderChangedConsumer.java` (`start`)
- **Affected symbols**: `OrderChangedConsumer.start`
- **Graph nodes**: `OrderChangedConsumer`, `OrderViewProjector`
- **Evidence** (FACT): `new MqttClient(url, clientId, new MemoryPersistence())` + `opts.setCleanSession(false)`. So sánh: inventory `TaskMqttSubscriber` dùng `MqttDefaultFilePersistence` + `setManualAcks(true)` (đúng chuẩn durable). Gateway dùng `cleanSession=true` + Memory (hợp lý vì WS phù du).
- **Current behavior**: Broker giữ session (clean=false) nhưng client mất map message khi restart (Memory) → có thể bỏ sót/nhân đôi QoS1. Rủi ro được giảm nhẹ bởi gap-recovery (ISSUE không) + projection ordering theo `aggregateVersion` (idempotent).
- **Expected behavior**: Persistence bền (file) nhất quán với `cleanSession=false`.
- **Root cause**: Cặp cấu hình mâu thuẫn.
- **Technical/Business impact**: Event order-changed có thể trễ/sót sau restart; read-model lạc hậu đến khi gap-recovery bù.
- **Recommended solution** (DESCRIBED): Dùng `MqttDefaultFilePersistence` + manual acks như inventory, HOẶC đặt `cleanSession=true` nếu đã dựa hoàn toàn vào outbox+gap-recovery.
- **Files to modify**: `OrderChangedConsumer`.
- **Rollback**: Revert.
- **Verification**: Restart consumer giữa luồng event → không mất cập nhật (hoặc gap-recovery bù).
- **Remaining risks**: Thấp (có gap-recovery).
- **Dependencies**: ISSUE-02.

---

## ISSUE-06: Event `identity.command.result` được phát nhưng KHÔNG có consumer (orphan producer)

- **Severity**: MEDIUM
- **Category**: INTEGRATION
- **Confidence**: HIGH
- **Status**: OPEN
- **Module**: `smartfarm-identity-service` (messaging)
- **Affected files**: `services/smartfarm-identity-service/.../messaging/command/IdentityMqttCommandDispatcher.java` (`publishResult`), `.../messaging/event/IdentityEventTopics.java`
- **Affected symbols**: `IdentityMqttCommandDispatcher.publishResult`, `IdentityEventTopics.domain`
- **Evidence** (FACT): Dispatcher gọi `events.publish(..., "identity.command.result", ...)`. Topic sinh ra: `smartfarm/{tenant}/_global/domain/identity-result/v1`. `grep` toàn repo không có consumer nào subscribe loại event này (chỉ gateway WS filter `smartfarm/+/+/domain/+/+` republish lên WebSocket — INFERENCE: chỉ hiển thị, không có xử lý nghiệp vụ phía service).
- **Current behavior**: Kết quả lệnh identity phát ra bus nhưng không service nào tiêu thụ (ngoài WS forward).
- **Expected behavior**: Hoặc có consumer (caller nhận kết quả), hoặc tài liệu hoá đây là event chỉ-quan-sát.
- **Root cause**: Vòng đời command result chưa khép (không có cơ chế callback tới caller gửi command).
- **Impact**: Caller gửi MQTT command không có đường nhận kết quả xác định (business flow hở).
- **Recommended solution** (DESCRIBED): Định nghĩa consumer kết quả cho caller, hoặc đánh dấu rõ "observation-only" + xoá khỏi backlog nếu đúng chủ đích.
- **Files to modify**: Tài liệu + (nếu cần) consumer mới.
- **Verification**: Vẽ lại producer/consumer graph (xem `02-event-flow-report.md`).
- **Remaining risks**: UNKNOWN — ý định thiết kế của event result.
- **Dependencies**: ISSUE-02.

---

## ISSUE-07: Trùng lặp AutoConfiguration security gRPC (hai class cùng tên khác package)

- **Severity**: MEDIUM
- **Category**: MAINTAINABILITY
- **Confidence**: HIGH
- **Status**: OPEN
- **Module**: `libs/smartfarm-security`
- **Affected files**:
  - `.../security/grpc/autoconfigure/SmartFarmGrpcSecurityAutoConfiguration.java` (đăng ký trong imports)
  - `.../security/autoconfigure/SmartFarmGrpcSecurityAutoConfiguration.java` (KHÔNG trong imports)
  - `.../META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- **Evidence** (FACT): Tồn tại hai class **cùng tên** `SmartFarmGrpcSecurityAutoConfiguration` ở hai package; nội dung gần như trùng (bean `JwtServerInterceptor`, correlation, audit). File `AutoConfiguration.imports` chỉ liệt kê bản `grpc.autoconfigure` + `SmartFarmSecurityPropertiesAutoConfiguration`. → Bản `security.autoconfigure` là **dead code** (không được nạp).
- **Current behavior**: Không gây double-wiring (chỉ 1 bản được import), nhưng gây nhầm lẫn/nguy cơ sửa nhầm file.
- **Expected behavior**: Một bản auto-config duy nhất.
- **Root cause**: Di cư package chưa dọn file cũ.
- **Impact**: Bảo trì khó; rủi ro ai đó thêm bản cũ vào imports → double global interceptor (double auth/audit).
- **Recommended solution** (DESCRIBED): Xoá class không dùng ở `security.autoconfigure` (giữ bản `grpc.autoconfigure`).
- **Files to modify**: Xoá 1 file.
- **Rollback**: Revert.
- **Verification**: Build; chỉ 1 `JwtServerInterceptor` bean.
- **Remaining risks**: Thấp.
- **Dependencies**: Không.

---

## ISSUE-08: Hai triển khai saga song song (`OrderSagaOrchestrator` inline vs. persistent worker)

- **Severity**: MEDIUM
- **Category**: ARCHITECTURE
- **Confidence**: CONFIRMED
- **Status**: RESOLVED (2026-10-02 — inline path removed; single saga source of truth)
- **Module**: `smartfarm-order-service` (saga)
- **Affected files**: `saga/OrderSagaOrchestrator.java`, `saga/worker/*`, `grpc/FarmOrderGrpcService.java`
- **Affected symbols**: `OrderSagaOrchestrator`, `OrderSagaPersistentWorker`, `FarmOrderGrpcService`
- **Graph nodes**: `OrderSagaOrchestrator` (community 8), `OrderSagaTransactionService` (god-node deg 49)
- **Evidence** (FACT): `FarmOrderGrpcService` inject cả `OrderSagaOrchestrator saga` lẫn `OrderSagaTransactionService persistentSagas`, nhưng `placeOrder` chỉ dùng `persistentSagas.create`. `OrderSagaOrchestrator.run` không được gọi trong gRPC surface đã đọc.
- **Current behavior**: Logic forward/compensation bị nhân đôi; bản inline tiềm ẩn drift (xem ISSUE-03).
- **Expected behavior**: Một nguồn chân lý cho saga.
- **Root cause**: Chuyển từ inline sang persistent worker chưa gỡ bản cũ.
- **Impact**: Tăng bề mặt lỗi, dễ sửa nhầm, god-node tập trung rủi ro.
- **Recommended solution** (DESCRIBED): Deprecate + loại bỏ đường inline; bỏ injection khỏi `FarmOrderGrpcService`.
- **Files to modify**: `FarmOrderGrpcService`, `OrderSagaOrchestrator`.
- **Verification**: Build + full saga test suite.
- **Remaining risks**: UNKNOWN caller test.
- **Dependencies**: ISSUE-03.

- **RESOLUTION (2026-10-02)**: Đường inline `OrderSagaOrchestrator` đã bị gỡ hoàn toàn (xem ISSUE-03 RESOLUTION). Giờ chỉ còn MỘT saga: persistent worker. Không còn nhân đôi forward/compensation. Build+test xanh. Dừng tách `smartfarm-saga` (chỉ 1 owner) — backlog gated `SAGA-EN2` trong `docs/audit/08`.

---

## ISSUE-09: `IdentityMqttCommandDispatcher` cũng head-of-line block khi lỗi runtime (break)

- **Severity**: MEDIUM
- **Category**: RELIABILITY
- **Confidence**: HIGH
- **Status**: RESOLVED (2026-10-03 — shared classifier; unknown error now continue, not break)
- **Module**: `smartfarm-identity-service` (messaging/command)
- **Affected files**: `.../messaging/command/IdentityMqttCommandDispatcher.java` (`dispatch`)
- **Evidence** (FACT): Trong `dispatch()`, nhánh `catch (RuntimeException)` gọi `markFailed` rồi **`break;`** — giống ISSUE-04. `IllegalArgumentException` được xử lý riêng (markFailed, không break). Lỗi runtime khác (vd DB tạm) sẽ dừng batch.
- **Current behavior**: Một command lỗi runtime chặn phần còn lại của batch cho tới vòng sau.
- **Expected behavior**: Chỉ dừng khi lỗi hạ tầng rõ ràng; cô lập command lỗi.
- **Impact**: Trễ xử lý command membership/role.
- **Recommended solution** (DESCRIBED): Phân loại lỗi; `continue` cho lỗi dữ liệu, `break` chỉ cho lỗi hạ tầng; cân nhắc giới hạn retry → DEAD.
- **Verification**: Chèn 1 command lỗi + nhiều command hợp lệ.
- **Remaining risks**: Thấp.
- **Dependencies**: ISSUE-04 (cùng mẫu).

---

## ISSUE-10: `OrderMqttPublisher` dùng `cleanSession=true` + client-id ngẫu nhiên — không có session bền phía publisher

- **Severity**: MEDIUM
- **Category**: RELIABILITY
- **Confidence**: MEDIUM
- **Status**: OPEN
- **Module**: `smartfarm-order-service` (outbox)
- **Affected files**: `.../outbox/OrderMqttPublisher.java`
- **Evidence** (FACT): `new MqttClient(url, "smartfarm-order-" + UUID.randomUUID(), MemoryPersistence)`, `setCleanSession(true)`, QoS1, `setRetained(false)`. Publish đồng bộ, bọc bởi outbox (at-least-once ở DB).
- **Current behavior**: Độ bền do outbox đảm nhận; publisher chỉ cần đẩy QoS1. Client-id ngẫu nhiên ⇒ nếu disconnect giữa publish, không có inflight-recovery phía client (nhưng outbox `recoverStaleClaims` xử lý).
- **Expected behavior**: Chấp nhận được vì outbox là nguồn chân lý; ghi chú để tránh hiểu nhầm.
- **Impact**: Thấp; chủ yếu là rõ ràng hoá đảm bảo giao hàng.
- **Recommended solution** (DESCRIBED): Giữ nguyên nhưng tài liệu hoá "delivery guarantee = outbox at-least-once + consumer idempotency". Không cần session bền phía publisher.
- **Remaining risks**: Thấp.
- **Dependencies**: ISSUE-02, ISSUE-05.

---

## ISSUE-11: `JwtServerInterceptor` không kiểm audience theo đích service — phụ thuộc hoàn toàn vào `SmartFarmJwtValidator`

- **Severity**: MEDIUM
- **Category**: SECURITY
- **Confidence**: MEDIUM
- **Status**: OPEN
- **Module**: `libs/smartfarm-security`
- **Affected files**: `.../grpc/JwtServerInterceptor.java`, `.../jwt/SmartFarmJwtValidator.java`, `.../jwt/JwtSecurityFactory.java`
- **Evidence** (FACT): `JwtServerInterceptor` kiểm bearer + authority + so khớp `x-tenant-id`. Việc kiểm audience nằm ở `SmartFarmJwtValidator.validate` (`audiences::contains` theo cấu hình service). Vì vậy nếu một service cấu hình `audiences` quá rộng (vd gồm cả `smartfarm-gateway`), token phát cho gateway có thể gọi service đó.
- **Current behavior**: Order-service `application-dev.yml`... (xem ISSUE-01) — ở dev order không có khối jwt riêng cho order audience trong `application.yml`; identity/gateway cấu hình `audiences: [smartfarm-gateway, smartfarm-identity]`.
- **Expected behavior**: Mỗi resource server chỉ chấp nhận audience của chính nó (vd order chỉ `smartfarm-order`).
- **Root cause**: `audiences` là tập hợp, dễ cấu hình rộng; interceptor không ràng buộc thêm.
- **Security impact**: Token cross-audience có thể bị dùng chéo nếu cấu hình rộng (RISK).
- **Recommended solution** (DESCRIBED): Chuẩn hoá mỗi service chỉ liệt kê audience của mình; cân nhắc kiểm `identity.hasAudience(expected)` trong interceptor.
- **Verification**: Token audience=gateway gọi order → `invalid_token`.
- **Remaining risks**: UNKNOWN cấu hình audiences production order.
- **Dependencies**: ISSUE-01.

---

## ISSUE-12: `isSuperAdmin()` cấp bypass authority qua `SCOPE_*`

- **Severity**: MEDIUM
- **Category**: SECURITY
- **Confidence**: MEDIUM
- **Status**: OPEN
- **Module**: `libs/smartfarm-security`
- **Affected files**: `.../core/SecurityIdentity.java` (`isSuperAdmin`), `.../grpc/JwtServerInterceptor.java` (`hasRequiredAuthority`)
- **Evidence** (FACT): `hasRequiredAuthority` trả true nếu `identity.hasAuthority(required) || identity.isSuperAdmin()`; `isSuperAdmin()` = `authorities.contains("SCOPE_*")`.
- **Current behavior**: Bất kỳ token nào mang scope `*` (→ authority `SCOPE_*`) vượt mọi kiểm authority gRPC.
- **Expected behavior**: Super-admin phải được phát hành rất hạn chế + kiểm toán; cân nhắc tách khỏi scope thường.
- **Security impact**: Nếu đường phát token cho phép scope `*` lọt vào service token/tenant token → leo thang (RISK).
- **Recommended solution** (DESCRIBED): Rà soát nơi phát `*`; giới hạn `SCOPE_*` chỉ cho token nền tảng, log audit mỗi lần dùng bypass.
- **Verification**: Grep nơi cấp scope `*`; test token thường không có `SCOPE_*`.
- **Remaining risks**: UNKNOWN chính sách phát scope `*` (ngoài phạm vi file đã đọc).
- **Dependencies**: Không.

---

## ISSUE-13: Thiếu timeout/circuit-breaker ở consumer MQTT; deadline gRPC cố định 5s không cấu hình được

- **Severity**: MEDIUM
- **Category**: RELIABILITY
- **Confidence**: HIGH
- **Status**: OPEN
- **Module**: `smartfarm-order-service` (saga executors), toàn bộ MQTT consumer
- **Affected files**: `saga/worker/OrderSagaForwardStepExecutor.java`, `OrderSagaCompensationStepExecutor.java`, `saga/OrderSagaOrchestrator.java`
- **Evidence** (FACT): Mọi call gRPC downstream dùng `.withDeadlineAfter(5, TimeUnit.SECONDS)` hard-code. Không có circuit-breaker/backoff ở tầng client (backoff retry nằm ở tầng saga step, không ở tầng call). Consumer MQTT xử lý đồng bộ trong callback (`projector.apply`) không có timeout.
- **Current behavior**: Deadline cố định; retry/backoff do saga `retryDelay` (exponential, có cap) đảm nhận ở mức step.
- **Expected behavior**: Deadline cấu hình (đã có `smartfarm.security.grpc.default-deadline` nhưng không được dùng ở order), có fallback khi downstream down lâu.
- **Impact**: Thiếu linh hoạt vận hành; downstream chậm → step fail → retry (ổn), nhưng không có circuit-breaker gây dồn tải.
- **Recommended solution** (DESCRIBED): Đưa deadline ra config; cân nhắc Resilience4j circuit-breaker cho inventory/finance stub.
- **Remaining risks**: Thấp.
- **Dependencies**: ISSUE-01 (config security).

---

## ISSUE-14: Pagination `listOrders` — ĐÃ KẸP đúng (RESOLVED/INFO, không phải lỗi)

- **Severity**: INFO
- **Category**: PERFORMANCE
- **Confidence**: CONFIRMED
- **Status**: RESOLVED (xác minh bằng đọc source)
- **Module**: `smartfarm-order-service` (application/query)
- **Affected files**: `application/OrderQueryService.java` (L47)
- **Evidence** (FACT): `OrderQueryService.list` kẹp: `int pageSize = filter.pageSize() <= 0 ? 50 : Math.min(filter.pageSize(), 200);` và fetch `pageSize + 1` để tính `hasMore` (cursor-based). → Query **bounded**, không có nguy cơ unbounded.
- **Current behavior**: Mặc định 50, trần 200, cursor pagination — đúng chuẩn.
- **Recommended solution**: Không cần (ghi nhận là điểm tốt).
- **Remaining risks**: Không.
- **Dependencies**: Không.

---

## ISSUE-15: `ddl-auto` không nhất quán giữa dev (`update`) và prod (`validate`)

- **Severity**: LOW
- **Category**: CONFIGURATION
- **Confidence**: HIGH
- **Status**: OPEN
- **Module**: `order-service`, `identity-service`
- **Affected files**: `order application-dev.yml` (`ddl-auto: update`, `flyway.enabled: false`), `order application-prod.yml` (`validate`, flyway on), `identity` tương tự.
- **Evidence** (FACT): Dev tắt Flyway + Hibernate `update` (schema tự sinh); prod bật Flyway + `validate`. Rủi ro: schema dev lệch migration prod (migration không được kiểm ở dev).
- **Recommended solution** (DESCRIBED): Dùng Flyway cả ở dev (hoặc Testcontainers) để migration được chạy/kiểm thường xuyên.
- **Remaining risks**: Lệch schema âm thầm.
- **Dependencies**: Không.

---

## ISSUE-16: Mật khẩu/secret mặc định trong `application-dev.yml` (dev-only nhưng nằm trong repo)

- **Severity**: LOW
- **Category**: SECURITY
- **Confidence**: HIGH
- **Status**: OPEN
- **Module**: `order-service`, `identity-service`
- **Affected files**: `order application-dev.yml` (`secret: dev-order-service-secret`, DB `password: root`), `identity application.yml` (`bootstrap-password: replace-with-a-long-random-password`).
- **Evidence** (FACT): Secret dev hardcode. Prod dùng env var (tốt). Có nhãn "DEV ONLY".
- **Recommended solution** (DESCRIBED): Giữ nguyên nhưng đảm bảo không có profile dev nào chạy ở môi trường thật; cân nhắc `.env` cho dev.
- **Remaining risks**: Thấp nếu dev không lộ.
- **Dependencies**: Không.

---

## ISSUE-17: `IdentityMqttInbox`/outbox — xác nhận idempotency tốt; ghi nhận INFO (không phải lỗi)

- **Severity**: INFO
- **Category**: OBSERVABILITY
- **Confidence**: HIGH
- **Status**: INFO
- **Module**: `identity-service`
- **Evidence** (FACT): `IdentityMqttCommandIntake` validate chặt (size, topic parse, envelope, allowed-types, tenant-topic match, schema version, issued-at skew ≤300s) + dedup `existsById` + `DataIntegrityViolationException` → DUPLICATE. Đây là mẫu idempotency inbox đúng chuẩn.
- **Note**: Dùng làm hình mẫu cho các consumer khác (order read-model đã có inbox tương tự).

---

## ISSUE-18: Projection gap-recovery + ordered inbox là điểm mạnh (INFO)

- **Severity**: INFO
- **Category**: ARCHITECTURE
- **Confidence**: HIGH
- **Status**: INFO
- **Module**: `order-service` (readmodel/recovery)
- **Evidence** (FACT): `OrderProjectionApplyTransaction.receive` dedup theo `eventId`, bỏ qua version cũ (`<= current`), mở "gap" khi `version > current+1`, rồi `drain` tuần tự theo version với lock `forUpdate`. Có archive replay (`OrderEventArchiveEntity`) + worker gap-recovery có backoff/manual-review. Thiết kế CQRS ordered-projection vững.
- **Note**: Giảm nhẹ rủi ro ISSUE-05 (mất event QoS1).

---

## Những điểm cần người dùng xác nhận (UNKNOWN/BLOCKER)
- **UNKNOWN-A (một phần giải quyết)**: `grep` trong `deploy/kubernetes/order-gateway.yaml` KHÔNG thấy override `smartfarm.security.*`/`SMARTFARM_JWT*` ⇒ YAML trong jar là nguồn cấu hình → ISSUE-01 vẫn chặn boot prod. Vẫn cần xác nhận ConfigMap/Secret ngoài file này nếu có.
- **UNKNOWN-B (ĐÃ GIẢI QUYẾT → nâng mức ISSUE-02)**: `infra/mosquitto.conf` = `allow_anonymous true`, không ACL, không TLS. Broker mở hoàn toàn ⇒ ISSUE-02 CONFIRMED nghiêm trọng ở cấu hình mặc định.
- **UNKNOWN-C**: Có caller nào (test/admin) còn gọi `OrderSagaOrchestrator.run` không? → ISSUE-03/08.
- **UNKNOWN-D (ĐÃ GIẢI QUYẾT → ISSUE-14 RESOLVED)**: `OrderQueryService.list` kẹp pageSize (mặc định 50, trần 200). Không có nguy cơ unbounded.
- **UNKNOWN-E**: Chính sách phát hành scope `*` (super-admin) → ISSUE-12.

---

## ISSUE-19 (CFG-01): 6 `application-test.yml` dùng flat security key → service không boot ở profile `test`

> **NEW** — phát hiện ở incremental audit 2026-10-03 (HEAD `51f23a7`). Backlog: `backlog/v0.1/V0.1-001-*.md`.

- **Severity**: HIGH
- **Category**: CONFIGURATION
- **Confidence**: HIGH
- **Status**: OPEN
- **Module**: finance / health / inventory / livestock / order / reporting (`application-test.yml`)
- **Business capability**: CI / profile `test` reproducibility (không chặn prod — ISSUE-01 đã vá).
- **Affected files**: `services/{finance,health,inventory,livestock,order,reporting}-service/src/main/resources/application-test.yml`
- **Affected symbols**: `SmartFarmSecurityProperties` (compact constructor), `SmartFarmSecurityProperties.Jwt`
- **Evidence** (FACT): record yêu cầu nested `smartfarm.security.jwt.{issuer,jwk-set-uri,audiences(plural)}`; 6 file test viết flat/singular `issuer/jwk-set-uri/audience`. `identity` test YAML đúng (nested) → đối chứng.
- **Current behavior** (INFERENCE từ FACT): `SPRING_PROFILES_ACTIVE=test` ⇒ `jwt` bind null ⇒ ném `IllegalArgumentException("smartfarm.security.jwt is required")` ⇒ context fail. ITs hiện tại KHÔNG dính vì ghi đè nested `jwt.*` INLINE qua `@SpringBootTest(properties=...)` và không bật profile `test`.
- **Root cause**: Lặp lại pattern ISSUE-01 khi thêm `application-test.yml` mới (commit `51f23a7`).
- **Recommended solution** (DESCRIBED): đổi khối `security:` sang nested `jwt:` + `audiences:` list ở cả 6 file (patch mẫu trong backlog item). Reversible.
- **Verification**: boot 6 service với profile `test` + `mvn test` xanh.
- **Dependencies**: độc lập; BLOCKS CI test gate.
