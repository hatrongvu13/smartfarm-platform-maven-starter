# 06 — Remediation Plan (Kế hoạch khắc phục)

> Thứ tự theo backlog priority: data-loss/corruption → security holes → core-business interruption → tx/consistency → unclosed event/API flows → integration → performance → reliability/observability → tech debt → docs.
> Mọi thay đổi **backward-compatible, có rollback**. KHÔNG rewrite. Patch chỉ MÔ TẢ (không áp dụng trong pass này).

---

## Phase 0 — Blockers (phải xong trước khi lên prod)
- **Scope**: ISSUE-01 (order prod không boot).
- **Files**: `services/smartfarm-order-service/src/main/resources/application.yml`, `application-prod.yml`.
- **Order**:
  1. Xác nhận **UNKNOWN-A**: đọc `deploy/kubernetes/order-gateway.yaml` + ConfigMap xem có ghi đè `smartfarm.security.*` không.
  2. Sửa YAML sang dạng lồng `smartfarm.security.jwt.{issuer,jwk-set-uri,audiences}`; `audiences: [smartfarm-order]`.
- **Risk**: Thấp (chỉ YAML); nếu k8s đã ghi đè đúng dạng → chỉ cần sửa file base cho nhất quán.
- **Compatibility**: Không ảnh hưởng service khác (đã đúng dạng).
- **Rollback**: Revert 2 file.
- **Verification**: Boot `SPRING_PROFILES_ACTIVE=prod` với env đầy đủ → readiness UP; gRPC không token → `UNAUTHENTICATED`; token sai audience → `invalid_token`.
- **Done-criteria**: order-service boot prod + 1 smoke `PlaceOrder` qua gateway thành công.

---

## Phase 1 — Security correctness
- **Scope**: ISSUE-02 (MQTT security), ISSUE-11 (audience), ISSUE-12 (super-admin bypass).
- **Files**: `security/mqtt/*`, mỗi consumer MQTT; `security/grpc/JwtServerInterceptor`; cấu hình `audiences` các service; nơi phát scope `*`.
- **Order**:
  1. Xác nhận **UNKNOWN-B** (`infra/mosquitto.conf`): nếu broker đã mTLS+ACL chặt theo tenant → hạ ISSUE-02 xuống MEDIUM, ưu tiên tài liệu hoá.
  2. Triển khai `MqttSecurityEnvelope` + `MqttSecurityVerifier` (JWS/HMAC) sau cờ `smartfarm.security.mqtt.verify-enabled=false` (bật dần theo môi trường).
  3. Siết `audiences` mỗi service về đúng audience riêng; (tùy chọn) thêm kiểm `identity.hasAudience(expected)` trong interceptor.
  4. Rà soát (UNKNOWN-E) nơi phát `SCOPE_*`; thêm audit log mỗi lần super-admin bypass.
- **Risk**: Trung bình (ảnh hưởng đường vào event + token); giảm bằng cờ bật/tắt.
- **Compatibility**: Cờ mặc định off → không gãy môi trường hiện tại.
- **Rollback**: Tắt cờ / revert cấu hình audiences.
- **Verification**: Message giả bị drop; token cross-audience bị từ chối; log audit super-admin xuất hiện.
- **Done-criteria**: Có cơ chế xác thực message (hoặc ACL/mTLS được xác nhận) + audience thu hẹp.

---

## Phase 2 — Tx / consistency (saga)
- **Scope**: ISSUE-03 (key đảo finance), ISSUE-08 (dual-path).
- **Files**: `order/saga/OrderSagaOrchestrator.java`, `order/grpc/FarmOrderGrpcService.java`, test compensation.
- **Order**:
  1. Xác nhận **UNKNOWN-C**: grep mọi caller `OrderSagaOrchestrator.run/compensate` (gồm test/admin).
  2. Nếu không còn caller nghiệp vụ → gỡ injection `saga` khỏi `FarmOrderGrpcService` + deprecate class.
  3. Nếu còn → đồng bộ quy ước key đảo về `":finance"`.
  4. Thêm test đảm bảo một nguồn chân lý saga (IMP-18).
- **Risk**: Trung bình (động vào saga); có test hardening sẵn (`OrderSagaEntityHardeningTest`, ...).
- **Rollback**: Revert.
- **Verification**: Full saga test suite xanh; mô phỏng fail finance → compensation đảo đúng key.
- **Done-criteria**: Chỉ một đường saga; không còn divergence key.

---

## Phase 3 — Unclosed event / API flows
- **Scope**: ISSUE-06 (identity command-result orphan), event-flow topic mismatch (02 §2/§4), ISSUE-12 topic filter gateway.
- **Files**: gateway `MqttEventSubscriber` filter; identity command-result; tài liệu event.
- **Order**:
  1. Đổi filter gateway `smartfarm/+/+/domain/#` (bắt mọi độ sâu) — fix mismatch 6 vs 7 segment.
  2. Khép `identity.command.result`: thêm consumer hoặc đánh dấu observation-only.
- **Risk**: Thấp.
- **Rollback**: Revert filter/config.
- **Verification**: Vẽ lại producer/consumer graph; order business lifecycle event tới WS; không còn orphan ngoài chủ đích.
- **Done-criteria**: Mọi event có consumer hoặc được ghi nhận observation-only.

---

## Phase 4 — Integration (độ bền consumer/relay)
- **Scope**: ISSUE-04, ISSUE-09 (head-of-line), ISSUE-05 (persistence consumer).
- **Files**: `order/outbox/OrderOutboxRelay`, `identity/.../IdentityMqttCommandDispatcher`, `order/readmodel/OrderChangedConsumer`.
- **Order**:
  1. Tách lỗi dữ liệu (`IllegalArgumentException`) → DEAD + `continue`; chỉ `MqttException`/lỗi hạ tầng mới `break`.
  2. Đồng nhất persistence order consumer với inventory (file persistence + manual acks) hoặc cleanSession=true.
- **Risk**: Thấp; cải thiện throughput + khả năng phục hồi.
- **Rollback**: Revert.
- **Verification**: Chèn poison + nhiều event hợp lệ → event hợp lệ vẫn chảy; restart consumer → không mất cập nhật (hoặc gap-recovery bù).
- **Done-criteria**: Không còn head-of-line blocking; persistence nhất quán.

---

## Phase 5 — Performance
- **Scope**: ISSUE-13 (deadline/circuit-breaker), ISSUE-14 (pageSize).
- **Files**: `order/saga/worker/*`, `order/application/OrderQueryService`.
- **Order**:
  1. Xác nhận **UNKNOWN-D**: đọc `OrderQueryService.list` xem có kẹp pageSize.
  2. Đưa deadline gRPC ra config; thêm circuit-breaker (Resilience4j) cho inventory/finance.
- **Risk**: Thấp–trung bình.
- **Verification**: Load test pageSize lớn bị kẹp; downstream chậm không gây dồn tải vô hạn.
- **Done-criteria**: Query bounded; call có deadline cấu hình + CB.

---

## Phase 6 — Reliability / Observability
- **Scope**: IMP-16 (alerts), ISSUE-10 (tài liệu delivery), ISSUE-15 (ddl-auto dev/prod).
- **Files**: `observability/prometheus/*`, README order, YAML dev.
- **Order**: Thêm alert outbox DEAD / projection gap / saga manual-review; dùng Flyway ở dev.
- **Risk**: Thấp.
- **Done-criteria**: Dashboard/alert phủ các chỉ số rủi ro; dev chạy migration thật.

---

## Phase 7 — Tech debt
- **Scope**: ISSUE-07 (dead autoconfig), IMP-15 (`TASK_SCHEDULED`).
- **Files**: `security/autoconfigure/SmartFarmGrpcSecurityAutoConfiguration` (xoá), `OrderDomainStatus`.
- **Risk**: Thấp.
- **Done-criteria**: Dead code dọn sạch; state machine phản ánh đúng nghiệp vụ.

---

## Phase 8 — Docs
- **Scope**: Cập nhật README order/identity về delivery-guarantee, saga path duy nhất, event topics chuẩn; ghi chú secret dev-only (ISSUE-16).
- **Done-criteria**: Tài liệu khớp hiện trạng sau Phase 0–7.

---

## Ma trận phụ thuộc (tóm tắt)
- Phase 0 độc lập, làm trước.
- Phase 1 cần UNKNOWN-B/E; Phase 2 cần UNKNOWN-C; Phase 5 cần UNKNOWN-D.
- Phase 3/4 độc lập, có thể song song sau Phase 0.
- Phase 6–8 sau cùng.
