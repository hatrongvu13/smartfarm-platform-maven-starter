# 05 — Improvement Register (Sổ đăng ký cải tiến)

> Cải tiến (không nhất thiết là lỗi): file/class/method/module · lý do · giải pháp đề xuất · ưu tiên · phụ thuộc · ước lượng công sức.
> Ưu tiên: P1 (cao) → P4 (thấp). Effort: S (<0.5d), M (0.5–2d), L (2–5d).

| ID | Scope (file/class/method) | Lý do | Giải pháp đề xuất | Ưu tiên | Phụ thuộc | Effort |
|---|---|---|---|---|---|---|
| IMP-01 | `order` `application.yml`/`application-prod.yml` | Key security sai cấu trúc (ISSUE-01) | Chuyển sang `smartfarm.security.jwt.*` dạng lồng; thêm test context prod | P1 | UNKNOWN-A (k8s) | S |
| IMP-02 | `security.mqtt.MqttSecurityVerifier/Envelope` | Stub rỗng, không bảo vệ event (ISSUE-02) | Triển khai envelope ký + verifier, cờ `verify-enabled`; hoặc mTLS+ACL broker | P1 | UNKNOWN-B (mosquitto.conf) | L |
| IMP-03 | `order.saga.OrderSagaOrchestrator` + `FarmOrderGrpcService` | Dual saga path, drift key đảo finance (ISSUE-03/08) | Deprecate/loại bỏ đường inline; bỏ injection; test compensation | P1 | UNKNOWN-C | M |
| IMP-04 | `order.outbox.OrderOutboxRelay.poll` | Head-of-line blocking (ISSUE-04) | Phân loại lỗi dữ liệu vs hạ tầng; `DEAD`+`continue` cho poison, `break` chỉ cho `MqttException` | P2 | — | S |
| IMP-05 | `identity.messaging.command.IdentityMqttCommandDispatcher.dispatch` | Head-of-line blocking (ISSUE-09) | Như IMP-04; giới hạn retry→DEAD | P2 | — | S |
| IMP-06 | `order.readmodel.OrderChangedConsumer.start` | Persistence mâu thuẫn cleanSession=false+Memory (ISSUE-05) | Dùng `MqttDefaultFilePersistence`+manualAcks (như inventory) hoặc cleanSession=true | P2 | — | S |
| IMP-07 | `identity` command-result flow | Orphan producer (ISSUE-06) | Thêm consumer kết quả hoặc tài liệu observation-only | P3 | ISSUE-02 | M |
| IMP-08 | `security.autoconfigure.SmartFarmGrpcSecurityAutoConfiguration` | Dead duplicate autoconfig (ISSUE-07) | Xoá class không được import | P3 | — | S |
| IMP-09 | `security.grpc.JwtServerInterceptor` + cấu hình `audiences` | Audience rộng dễ dùng chéo (ISSUE-11) | Mỗi service chỉ liệt kê audience của mình; cân nhắc `hasAudience(expected)` trong interceptor | P2 | ISSUE-01 | M |
| IMP-10 | `security.core.SecurityIdentity.isSuperAdmin` | Bypass qua `SCOPE_*` (ISSUE-12) | Rà soát nơi phát scope `*`; audit mỗi lần bypass; tách super-admin khỏi scope thường | P2 | UNKNOWN-E | M |
| IMP-11 | `order.saga.worker.*` deadline gRPC hard-code 5s (ISSUE-13) | Không cấu hình, không circuit-breaker | Đưa deadline ra config (dùng `smartfarm.security.grpc.default-deadline`); thêm Resilience4j CB cho inventory/finance | P3 | — | M |
| IMP-12 | gateway `MqttEventSubscriber` topic-filter | Mismatch 6 vs 7 segment với order business topic (ISSUE-02 §2/§4) | Đổi filter `smartfarm/+/+/domain/#` | P2 | — | S |
| IMP-13 | `order.application.OrderQueryService.list` / `listOrders` | pageSize có thể unbounded (ISSUE-14) | Kẹp pageSize (1..200) + mặc định; xác nhận bằng đọc source | P3 | UNKNOWN-D | S |
| IMP-14 | `order`/`identity` dev vs prod `ddl-auto` (ISSUE-15) | Dev `update`+flyway off → lệch migration | Dùng Flyway/Testcontainers ở dev | P3 | — | M |
| IMP-15 | `OrderDomainStatus.TASK_SCHEDULED` | Trạng thái không được saga đi qua (03 §A4) | Làm rõ: xoá nếu chết, hoặc thêm step task-schedule nếu là nghiệp vụ | P4 | UNKNOWN | M |
| IMP-16 | Observability | `OrderObservabilityProperties`/metrics đã có; cân nhắc chuẩn hoá cảnh báo | Thêm alert cho outbox DEAD count, projection gap, saga manual-review | P3 | — | M |
| IMP-17 | Tài liệu delivery-guarantee | `OrderMqttPublisher` (ISSUE-10) dễ hiểu nhầm | Ghi chú rõ "outbox at-least-once + consumer idempotency" trong README order | P4 | — | S |
| IMP-18 | Test coverage compensation | Giảm rủi ro ISSUE-03 | Thêm test đảm bảo chỉ một đường saga + key đảo khớp | P2 | IMP-03 | M |

## Ghi chú ưu tiên
- Các mục **P1** (IMP-01/02/03) là tiền đề cho vận hành prod an toàn.
- **P2** là các sửa độ-tin-cậy/bảo-mật nhỏ, backward-compatible, rủi ro thấp.
- Không có mục nào yêu cầu rewrite; tất cả là thay đổi khu trú.
