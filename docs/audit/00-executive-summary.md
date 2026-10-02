# 00 — Executive Summary (Tóm tắt điều hành)

> Audit risk-first trên monorepo SmartFarm (Spring Boot 4.1.1 / Java 17, gRPC + MQTT, Postgres 17, Flyway).
> Phạm vi pass này: `order-service`, `identity-service`, `libs/smartfarm-security`, và event-flow toàn nền tảng.
> Nguyên tắc: chỉ bằng chứng; mọi khẳng định gắn nhãn **FACT/INFERENCE/UNKNOWN/RISK/BLOCKER**; **không sửa source** — chỉ mô tả patch.

## Tình trạng tổng thể
Nền tảng đã trưởng thành về mặt kiến trúc: order-service có **saga bền (persistent worker)**, **outbox at-least-once**, **CQRS ordered-projection + gap-recovery + archive replay**; identity-service có **MFA/TOTP, RBAC, service-token, MQTT command inbox idempotent**; `libs/smartfarm-security` có **JwtServerInterceptor fail-closed** và **GrpcMethodPolicy** per-method. Đây là các điểm mạnh thực (xem ISSUE-17, ISSUE-18).

Tuy nhiên pass này phát hiện **một lỗi cấu hình chặn khởi động ở prod** cho order-service, **tầng bảo mật MQTT là stub rỗng**, và một số **mẫu độ-bền/độ-tin-cậy lặp lại** (head-of-line blocking ở relay/dispatcher; persistence MQTT mâu thuẫn). Hầu hết là **sửa nhỏ, backward-compatible**.

## Mức độ hoàn thiện theo scope
| Scope | Mức bao phủ audit | Hoàn thiện nghiệp vụ | Ghi chú |
|---|---|---|---|
| order-service (saga/outbox/readmodel/recovery) | **FULL** | Saga forward+compensation: COMPLETE (đường worker). Dual-path inline: TECHNICAL_DEBT | Đọc toàn bộ core + migrations V1/V11 + 3 YAML |
| identity-service (auth/RBAC/MFA/token/mqtt) | **PARTIAL→FULL** | Auth/RBAC/MQTT-command: COMPLETE; command-result flow: PARTIAL (orphan) | Đọc AuthController, TokenService, ServiceToken, grpc policy, mqtt intake/dispatcher, event publisher; MFA đọc ở mức cấu trúc |
| libs/smartfarm-security | **FULL** | gRPC/JWT: COMPLETE; MQTT security: NOT_IMPLEMENTED | Đọc interceptor, policy, validator, factory, autoconfigure, web, issuer, mqtt stubs |
| event-flow platform-wide | **FULL** | Phần lớn khớp producer/consumer; 1 orphan + 1 thiếu verify | Đối chiếu topic string ở gateway/inventory/order/identity |

## Số lượng vấn đề theo mức độ
- **CRITICAL: 3** — ISSUE-01 (prod không boot), ISSUE-02 (MQTT security stub), ISSUE-03 (key đảo finance không nhất quán giữa 2 đường saga).
- **HIGH: 2** — ISSUE-04 (outbox head-of-line blocking), ISSUE-05 (consumer MQTT persistence mâu thuẫn).
- **MEDIUM: 8** — ISSUE-06..13.
- **LOW: 3** — ISSUE-14..16.
- **INFO: 2** — ISSUE-17, ISSUE-18.
- Tổng: **18 mục** (đánh số ISSUE-01..ISSUE-18).

## Rủi ro cao nhất (top risks)
1. **ISSUE-01 (CRITICAL)** — `order-service` dùng key cấu hình security sai cấu trúc (`smartfarm.security.issuer/jwk-set-uri/audience` phẳng) thay vì dạng lồng `smartfarm.security.jwt.*` mà record `SmartFarmSecurityProperties` yêu cầu. Ở profile **prod không có khối `jwt`** ⇒ compact constructor ném "`smartfarm.security.jwt is required`" ⇒ **service không khởi động**. Đây là **finding CRITICAL cao nhất** — chặn toàn bộ nghiệp vụ đơn hàng ở production.
2. **ISSUE-02 (CRITICAL/SECURITY)** — `MqttSecurityVerifier`/`MqttSecurityEnvelope` rỗng, không được tham chiếu ở đâu ⇒ event bus không có xác thực/toàn vẹn message. Mức nghiêm trọng thực tế phụ thuộc ACL broker (UNKNOWN-B).
3. **ISSUE-03 (CRITICAL/RELIABILITY)** — hai đường saga song song với quy ước idempotency-key đảo finance khác nhau (`:expense` vs `:finance`); hiện đường inline không được gọi nên rủi ro tiềm ẩn, nhưng nếu tái dùng → lệch sổ finance.

## Module rủi ro nhất
`smartfarm-order-service` — vừa là **owner saga + outbox + projection** (nhiều god-node: `OrderEntity` deg 94, `OrderSagaStepEntity` 67, `OrderSagaEntity` 63, `OrderOutboxEntity` 50, `OrderSagaTransactionService` 49), vừa mang **ISSUE-01 (prod outage)** và **ISSUE-03/04/05**. Tập trung rủi ro vận hành cao nhất.

## Luồng chưa hoàn chỉnh (incomplete flows)
- **MQTT security** (NOT_IMPLEMENTED) — ISSUE-02.
- **identity command-result** (PARTIAL — orphan producer, không consumer) — ISSUE-06.
- **Dual saga path** (TECHNICAL_DEBT, drift tiềm ẩn) — ISSUE-03/08.

## Hành động tiếp theo (next actions) — xem `06-remediation-plan.md`
1. **Phase 0 (blocker)**: Vá ISSUE-01 (YAML order prod/base → dạng lồng) + xác nhận UNKNOWN-A (k8s overrides).
2. **Security/correctness**: ISSUE-02 (triển khai hoặc khoá bằng ACL/mTLS + xác nhận UNKNOWN-B), ISSUE-03 (hợp nhất saga path).
3. **Reliability**: ISSUE-04/09 (bỏ head-of-line blocking), ISSUE-05 (persistence consumer).
4. **Integration/API closure**: ISSUE-06 (khép command-result), ISSUE-11/12 (audience & super-admin).
5. **Tech debt/docs**: ISSUE-07/08/10/13/14/15/16.

## Giới hạn & điều cần người dùng quyết (BLOCKER/UNKNOWN)
- MCP server `@kirocrew-computer` **không khả dụng** trong phiên này (khai báo trong agent spec nhưng chưa cấu hình) — không ảnh hưởng audit vì mọi phân tích dựa trên đọc source + `graphify` CLI.
- UNKNOWN-A..E (liệt kê cuối `04-issue-register.md`) cần người dùng xác nhận trước khi vá ISSUE-01/02/03/12/14.
