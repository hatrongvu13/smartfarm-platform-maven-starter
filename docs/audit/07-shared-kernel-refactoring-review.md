# Shared Kernel & Refactoring Review — SmartFarm Platform

> Phương pháp: chỉ dựa trên source + `pom.xml` + graphify graph (`graphify-out/`). KHÔNG chạy `mvn dependency:tree`/scan env/docker/k8s trong lần này (theo yêu cầu xác nhận trước). `MAVEN_HOME` đã xác nhận = `/Users/jaxmac/sdk/apache-maven-3.9.16/bin`; chỉ dùng để build/test, không dùng để scan.
> Nhãn: FACT (xác nhận trong source) / INFERENCE / RISK / RECOMMENDATION.

## Executive Summary

### Scope
MQTT, gRPC, Security, Common Kernel, Messaging Architecture, Configuration Standardization — trên 13 module (`libs/*`, `platform/*`, `services/*`, `apps/smartfarm-gateway`).

### Key Findings
1. **MQTT client code trùng lặp 4–7 lần** (FACT): mỗi service tự viết `new MqttClient`/`MqttAsyncClient` + connect/retry/persistence riêng — `OrderMqttPublisher`, `livestock/MqttPublisher`, `health/PahoHealthEventPublisher`, `identity/IdentityMqttConnectionManager` (producer); `OrderChangedConsumer`, `inventory/TaskMqttSubscriber`, `gateway/MqttEventSubscriber` (consumer).
2. **Outbox pattern trùng lặp 3–4 lần** (FACT): `order/outbox`, `health/outbox`, `livestock/outbox`, `identity/messaging/outbox` — mỗi bộ có Entity + Repository + Relay + Scheduling + Status gần như song song. Có **3 enum `OutboxStatus` riêng** (`common.outbox.OutboxStatus`, `order.outbox.OrderOutboxStatus`, `identity...IdentityOutboxStatus`).
3. **Cấu hình phân mảnh + đặt tên không nhất quán** (FACT): 14 lớp `@ConfigurationProperties`, lẫn lộn hậu tố `Properties` vs `Settings`, prefix không theo chuẩn.
4. **Common kernel còn mỏng** (FACT): chỉ 11 class (exception, paging, event metadata, 1 OutboxStatus) — chưa gom retry/outbox/mqtt/messaging dùng chung.

### Severity Summary
```text
CRITICAL: 0
HIGH:     2   (REF-01 MQTT client duplication, REF-02 Outbox duplication)
MEDIUM:   3   (REF-03 config inconsistency, REF-04 prefix mismatch bug, REF-05 OutboxStatus split)
LOW:      2   (REF-06 naming Settings/Properties, REF-07 module layering)
INFO:     1   (REF-08 common-kernel còn mỏng)
```

---

## Existing Shared Components

### FACT
- `libs/smartfarm-common-kernel`: `BusinessException`/`ConflictException`/`ForbiddenOperationException`/`NotFoundException`/`ValidationException`, `PageQuery`/`PageResult`, `DomainEvent`/`EventMetadata`, `RequestMetadata`, `OutboxStatus`.
- `libs/smartfarm-security`: `JwtServerInterceptor`, `GrpcMethodPolicy`, `TokenVerifier`/`JwtTokenVerifier`, `SmartFarmSecurityProperties` (+`Jwt`/`Grpc`/`Correlation`), `Reactive/ServletResourceSecurity`, autoconfigure; **mới bổ sung** `MqttSecurityEnvelope`/`MqttSecurityVerifier`/`MqttSecurityProperties` + autoconfig.
- `libs/smartfarm-proto`: 12 proto contract (`order`, `identity`, `events`, `telemetry`, …) — shared contract tốt.

### INFERENCE
Shared layer đã có nền (security + proto + kernel) nhưng **messaging (outbox + mqtt) chưa được chia sẻ** — mỗi service tự cài. Đây là nguồn trùng lặp lớn nhất.

### RECOMMENDATION
Tạo module `smartfarm-messaging` gom outbox + mqtt client; mở rộng `common-kernel` cho retry/paging; giữ `smartfarm-security`/`smartfarm-proto` như hiện tại.

---

## Duplicate Implementations

| Area | Location A | Location B | Severity | Recommendation |
|---|---|---|---|---|
| MQTT producer | `order/outbox/OrderMqttPublisher` | `livestock/outbox/MqttPublisher`, `health/outbox/PahoHealthEventPublisher`, `identity/.../IdentityMqttConnectionManager` | HIGH | Gom `SmartFarmMqttPublisher` dùng chung vào `smartfarm-messaging` |
| MQTT consumer | `order/readmodel/OrderChangedConsumer` | `inventory/mqtt/TaskMqttSubscriber`, `gateway/events/MqttEventSubscriber` | HIGH | Gom `SmartFarmMqttSubscriber` + callback template |
| Outbox stack | `order/outbox/*` | `health/outbox/*`, `livestock/outbox/*`, `identity/messaging/outbox/*` | HIGH | Trừu tượng `OutboxEntity`/`OutboxRelay`/`OutboxScheduling` generic |
| OutboxStatus enum | `common.outbox.OutboxStatus` | `order...OrderOutboxStatus`, `identity...IdentityOutboxStatus` | MEDIUM | Hợp nhất về `common.outbox.OutboxStatus` |
| Retry/backoff props | `order.saga` initial/maximum-retry | `order.outbox`, `order...gap-recovery`, `identity.outbox` | MEDIUM | `RetryProperties` dùng chung |

---

## MQTT Review

### FACT
- 4 producer + 3 consumer tự quản vòng đời client; `cleanSession`/persistence không nhất quán (ISSUE-05/10 trong audit): order consumer `cleanSession=false` + `MemoryPersistence` (mâu thuẫn), inventory dùng `MqttDefaultFilePersistence` (đúng).
- Bảo mật message: trước đây stub rỗng; **đã triển khai** signed envelope + đã nối sign/verify vào toàn bộ 4 producer + 3 consumer (default off).

### RISK
Trùng lặp làm mỗi lần sửa (vd thêm TLS, đổi persistence) phải lặp lại ở ≥7 nơi → dễ lệch (chính ISSUE-05 là hệ quả).

### RECOMMENDATION
`smartfarm-messaging.mqtt`: `SmartFarmMqttPublisher` (sign tích hợp), `SmartFarmMqttSubscriber` (verify + ack template, chọn persistence theo cấu hình), bind `MqttProperties` chung.

---

## gRPC Review

### FACT
- `libs/smartfarm-security` đã chuẩn hóa gRPC auth: `JwtServerInterceptor` + `GrpcMethodPolicy` + `SmartFarmGrpcSecurityAutoConfiguration`; mỗi service có `*GrpcPolicyConfiguration` khai báo policy per-method.
- `Grpc.defaultDeadline` mặc định 5s (ISSUE-13: cố định, một số client tự đặt deadline riêng).

### RISK
Client gRPC (gateway→service, order→inventory/finance) cấu hình host/port/deadline rải rác; thiếu chuẩn retry/circuit-breaker chung.

### RECOMMENDATION
`smartfarm-grpc` (hoặc gói trong security): factory client chung + `GrpcProperties` (deadline/retry per-target). Giữ `GrpcMethodPolicy` ở `smartfarm-security`.

---

## Security Review

### FACT
- JWT/gRPC: tốt, fail-closed, có test. `SmartFarmSecurityProperties` là chuẩn chung đúng.
- MQTT security: đã triển khai (xem trên) + docs ACL/mTLS (`docs/security/mqtt-acl-mtls.md`).

### RISK
`MqttSecurityProperties` tách khỏi `SmartFarmSecurityProperties` (hai `@ConfigurationProperties` cùng cây `smartfarm.security.*`). Chấp nhận được, nhưng về lâu dài nên gộp `mqtt` thành thành phần lồng của `SmartFarmSecurityProperties`.

### RECOMMENDATION
Giai đoạn sau: chuyển `smartfarm.security.mqtt.*` thành record lồng `SmartFarmSecurityProperties.Mqtt` để một nguồn sự thật.

---

## Common Kernel Review

### Candidates (nên đưa vào kernel/messaging)
- `OutboxStatus` (hợp nhất 3 enum) — **đã có** ở kernel, cần các service dùng lại.
- Generic outbox: `AbstractOutboxEntity`, `OutboxRelayTemplate`, `OutboxScheduling`.
- `RetryProperties` (initial/maximum/backoff) — hiện lặp ở saga/outbox/gap-recovery.
- MQTT client wrapper.

### Anti-Candidates (KHÔNG nên gom)
- Logic nghiệp vụ riêng (saga orchestration của order, MFA/RBAC của identity) — đặc thù domain.
- `OrderSagaProperties`, `MfaProperties` — thuộc domain, giữ tại service.
- Proto contract — đã ở `smartfarm-proto`, không trộn vào kernel.

---

## Configuration Consolidation Opportunities

### Current State (FACT)
14 lớp `@ConfigurationProperties`, prefix rải rác, hậu tố `Properties`/`Settings` lẫn lộn. **Bug phát hiện (REF-04)**: `OrderOutboxProperties` dùng `prefix="smartfarm.outbox"` nhưng YAML order viết `smartfarm.order.outbox.*` → một số key outbox có thể không bind (cần xác minh runtime; nhãn RISK/HIGH-confidence).

### Proposed Properties Classes
```text
SecurityProperties      # đã có (SmartFarmSecurityProperties)
JwtProperties           # đã có (SmartFarmSecurityProperties.Jwt)
GrpcProperties          # đã có (SmartFarmSecurityProperties.Grpc) — mở rộng per-target
MqttProperties          # MỚI: broker url/username/password/persistence/clean-session
InboxProperties         # MỚI: dedup/persistence cho consumer
OutboxProperties        # gom order/health/livestock/identity về 1 shape
RetryProperties         # gom initial/maximum-retry/backoff
SagaProperties          # giữ tại order (domain)
ProjectionProperties    # giữ tại order (domain)
```

---

## Proposed Module Structure
```text
smartfarm-common-kernel   # exception, paging, event metadata, OutboxStatus, RetryProperties, AbstractOutbox*
smartfarm-security        # JWT/gRPC policy/resource security + MQTT envelope (hiện tại)
smartfarm-messaging       # MỚI: SmartFarmMqttPublisher/Subscriber, OutboxRelayTemplate, MqttProperties/InboxProperties/OutboxProperties
smartfarm-grpc            # (tuỳ chọn) client factory + GrpcProperties per-target
```

---

## Migration Impact Assessment

### LOW
- Hợp nhất `OutboxStatus` về kernel (đổi import).
- Chuẩn hóa đặt tên `*Properties`.
- Sửa prefix mismatch REF-04.

### MEDIUM
- Rút MQTT publisher/consumer về `smartfarm-messaging` (đổi bean, giữ API `publish`/callback).
- `RetryProperties`/`MqttProperties` chung.

### HIGH
- Trừu tượng hóa outbox relay generic (ảnh hưởng transaction boundary từng service — phải test kỹ saga/projection của order).

---

## Recommended Roadmap

### P0 - Security Foundation
- (ĐÃ XONG) MQTT signed envelope + wiring default-off; docs ACL/mTLS.
- Sửa REF-04 prefix mismatch outbox (nhỏ, backward-compatible).

### P1 - Messaging Consolidation
- Tạo `smartfarm-messaging`; chuyển 4 producer + 3 consumer sang client chung; giữ hành vi.

### P2 - gRPC Standardization
- `GrpcProperties` per-target + client factory; chuẩn deadline/retry.

### P3 - Configuration Standardization
- Gom `OutboxProperties`/`RetryProperties`/`MqttProperties`; đổi tên `Settings`→`Properties`.

### P4 - Common Kernel Cleanup
- Hợp nhất `OutboxStatus`; thêm `AbstractOutboxEntity`/`OutboxRelayTemplate`.

### P5 - Legacy Removal & Deprecation
- Gỡ `OrderSagaOrchestrator` đường chết (trùng ISSUE-03/08); xoá code outbox cũ sau khi service đã dùng template chung.

---

## Detailed Findings

### REF-01 — MQTT client code trùng lặp
**Category:** MAINTAINABILITY · **Severity:** HIGH · **Confidence:** CONFIRMED
**Affected Modules:** order, health, livestock, inventory, identity, gateway
**FACT:** 7 class tự tạo MQTT client (grep `new MqttClient|new MqttAsyncClient`).
**RISK:** sửa 1 hành vi phải lặp ≥7 nơi; là gốc của ISSUE-05/10.
**RECOMMENDATION:** `smartfarm-messaging.mqtt.SmartFarmMqttPublisher/Subscriber`.
**Migration Impact:** MEDIUM.

### REF-02 — Outbox pattern trùng lặp
**Category:** ARCHITECTURE · **Severity:** HIGH · **Confidence:** CONFIRMED
**Affected Modules:** order, health, livestock, identity
**FACT:** 4 bộ Entity/Repository/Relay/Scheduling song song.
**RISK:** lệch hành vi at-least-once giữa service.
**RECOMMENDATION:** `OutboxRelayTemplate` generic + `AbstractOutboxEntity`.
**Migration Impact:** HIGH (đụng transaction boundary order).

### REF-03 — Cấu hình phân mảnh
**Category:** CONFIGURATION · **Severity:** MEDIUM · **Confidence:** CONFIRMED
**FACT:** 14 `@ConfigurationProperties`, prefix/đặt tên không nhất quán.
**RECOMMENDATION:** chuẩn hóa theo "Proposed Properties Classes".
**Migration Impact:** LOW–MEDIUM.

### REF-04 — Prefix mismatch `OrderOutboxProperties`
**Category:** BUG/CONFIGURATION · **Severity:** MEDIUM · **Confidence:** HIGH
**FACT:** `OrderOutboxProperties` `prefix="smartfarm.outbox"`; YAML order `smartfarm.order.outbox.*`.
**RISK:** key outbox order có thể dùng default thay vì giá trị cấu hình (cần xác minh binding runtime).
**RECOMMENDATION:** đổi prefix về `smartfarm.order.outbox` hoặc dời YAML; thêm test binding.
**Migration Impact:** LOW.

### REF-05 — `OutboxStatus` tách 3 bản
**Category:** MAINTAINABILITY · **Severity:** MEDIUM · **Confidence:** CONFIRMED
**RECOMMENDATION:** hợp nhất về `common.outbox.OutboxStatus`.

### REF-06 — Đặt tên `Settings` vs `Properties`
**Category:** MAINTAINABILITY · **Severity:** LOW
**FACT:** `IdentitySettings`/`ReportingSettings`/`TaskDeadlineSettings` vs `*Properties`.

### REF-07 — Module layering
**Category:** ARCHITECTURE · **Severity:** LOW
**INFERENCE:** chưa có `smartfarm-messaging`/`smartfarm-grpc`; messaging bị nhét trong từng service.

### REF-08 — Common kernel còn mỏng
**Category:** INFO
**FACT:** 11 class; chưa gom retry/outbox/mqtt.

---

## Final Recommendation

### Must Do
- Sửa REF-04 (prefix mismatch — có thể là bug cấu hình thật).
- P1 Messaging Consolidation (gốc của nhiều lỗi reliability).

### Should Do
- P3 Configuration Standardization + REF-05 hợp nhất OutboxStatus.
- P2 gRPC per-target deadline/retry.

### Nice To Have
- `smartfarm-grpc` tách riêng; gộp `smartfarm.security.mqtt` vào `SmartFarmSecurityProperties.Mqtt`.
