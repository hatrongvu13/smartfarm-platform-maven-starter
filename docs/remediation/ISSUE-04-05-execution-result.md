# ISSUE-04 / ISSUE-05 — Execution Result (MQTT Outbox + Durable Consumer Reliability)

Ngày: 2026-10-03 · Branch: `main` · Maven: `/Users/jaxmac/sdk/apache-maven-3.9.16` (3.9.16 / JDK17)
Classification: FIX + EXTRACT_SHARED + CONFIGURE + DOCUMENT + VERIFY. (DEFER: không common hoá topic/mapper/business.)

## 1. Mục tiêu & kết quả

- **ISSUE-04** (head-of-line blocking ở Order Outbox Relay) → **RESOLVED**.
- **ISSUE-05** (OrderChangedConsumer hybrid cleanSession=false + MemoryPersistence + auto-ACK) → **RESOLVED** (Phương án A).
- **ISSUE-09** (Identity dispatcher cùng mẫu break vô điều kiện) → **RESOLVED** (dùng chung classifier).
- Hạ tầng dùng chung trích vào module mới **`libs/smartfarm-messaging`** (không chứa topic/mapper/business).

## 2. Module dùng chung mới — `libs/smartfarm-messaging`

Chỉ generic, không business. Đăng ký trong root `pom.xml` (reactor + dependencyManagement).

| Thành phần | Vai trò |
|---|---|
| `dispatch/DispatchFailureCategory` | PERMANENT_DATA / TRANSIENT_INFRASTRUCTURE / TRANSIENT_APPLICATION / UNKNOWN |
| `dispatch/DispatchFailureDecision` | record {category, markDead, retry, continueBatch, breakBatch} + factory `deadAndContinue`/`retryAndBreak`/`retryAndContinue`; invariant chống cờ mâu thuẫn |
| `dispatch/DispatchFailureClassifier` | interface (Strategy/Policy) |
| `dispatch/DefaultDispatchFailureClassifier` | MqttException→infra(break); IllegalArgumentException→permanent(dead+continue); else→unknown(retry+continue); hook `classifyServiceSpecific` cho app-error riêng, **Paho chỉ nằm ở đây** (Adapter) |
| `dispatch/ExponentialBackoff` | `min(max, initial*2^(attempt-1))` — gộp công thức đang lặp |
| `dispatch/DispatchErrorCodes` | sanitize/truncate error code lưu DB (không rò secret/payload) |
| `mqtt/MqttConsumerSettings` | typed settings + invariant: cấm `cleanSession=false + MEMORY`; FILE buộc có directory + stable clientId |
| `mqtt/PersistenceMode` | MEMORY \| FILE |
| `mqtt/MqttClientFactory` | Factory + Adapter: tạo `MqttClient`+`MqttConnectOptions`; FILE fail-fast nếu dir không ghi được (không im lặng fallback memory) |

Test: `DispatchClassifierTest` (7) + `MqttConsumerSettingsTest` (5) = 12, pass.

## 3. Exception classification matrix (dùng chung)

| Error | Category | DB action | Batch |
|---|---|---|---|
| `MqttException` (hoặc cause chain) | TRANSIENT_INFRASTRUCTURE | markFailed + next retry (backoff) | **break** |
| `IllegalArgumentException` (bad topic/payload/command) | PERMANENT_DATA | **markDead** ngay | **continue** |
| App-error service tự khai (override) | TRANSIENT_APPLICATION | markFailed + retry | continue |
| Khác | UNKNOWN | markFailed + retry (giới hạn max-attempts) | continue |

## 4. ISSUE-04 — Order Outbox Relay (FIX)

- `OrderOutboxRelay.poll()`: thay `catch(Exception)→markFailed;break;` bằng classify → `markDead+continue` (poison) / `markFailed+break` (infra) / `markFailed+continue` (unknown).
- Thêm `OrderOutboxEntity.dead(errorCode)` + `OrderOutboxTransactionService.markDead(...)` — dùng trạng thái `DEAD` sẵn có (KHÔNG migration).
- `claimBatch()` vốn chỉ lấy `NEW/FAILED` → DEAD row không bao giờ vào ready batch lại.
- Invariant giữ: chỉ mark published sau publish thành công; retry không tạo business event mới; ordering event hợp lệ không đổi (chỉ bỏ qua poison row).

## 5. ISSUE-05 — OrderChangedConsumer (Phương án A: durable)

- Thay `MemoryPersistence` → `MqttClientFactory` mode **FILE** (dir qua property `smartfarm.order.readmodel.persistence-dir`, default `${java.io.tmpdir}/smartfarm/order-readmodel`).
- `cleanSession=false` (giữ), **manual ACK bật** (`setManualAcks(true)`), stable clientId (giữ).
- ACK (`messageArrivedComplete`) **chỉ sau** `projector.apply(...)` trả về (transaction commit) hoặc verified-invalid (quarantine drop + ack để không redeliver vô hạn).
- Transient failure trong projector → **throw, KHÔNG ack** → broker redeliver; file persistence sống qua restart.
- Duplicate → projector no-op (version guard) rồi ack. Gap-recovery worker giữ nguyên (backstop).
- FILE dir không ghi được → factory ném → consumer init fail (log), **không im lặng fallback memory**.
- Giữ nguyên ISSUE-02 security: `mqttSecurity.verify(topic, payload)` vẫn gate trước apply.

**Replica/deployment convention:** clientId + persistence-dir phải DUY NHẤT mỗi instance. Hai instance dùng chung durable clientId đồng thời = KHÔNG hỗ trợ → chạy single active consumer hoặc partition topic. Gateway giữ Phương án B (ephemeral).

## 6. ISSUE-09 — Identity dispatcher (FIX, dùng chung semantic)

- `dispatch()`: gộp `catch(IllegalArgumentException)` + `catch(RuntimeException)` thành một, classify:
  permanent-data → `markFailed("COMMAND_PAYLOAD_INVALID") + continue` (giữ đúng contract cũ);
  infra → break; unknown → `markFailed + continue` (trước đây break vô điều kiện — đã sửa).
- KHÔNG đổi authorization/envelope/dedup của Identity; KHÔNG gộp JPA entity/repo Identity với Order.
- Identity inbox không có trạng thái DEAD → permanent-data map vào `markFailed` sẵn có (behavior-preserving cho IllegalArgumentException).

## 7. Build / test

```bash
export MAVEN_HOME=/Users/jaxmac/sdk/apache-maven-3.9.16
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
"$MAVEN_HOME/bin/mvn" -pl libs/smartfarm-messaging -am test                                  # 12 pass
"$MAVEN_HOME/bin/mvn" -pl services/smartfarm-order-service,services/smartfarm-identity-service,services/smartfarm-inventory-service -am -DskipTests compile  # EXIT=0
"$MAVEN_HOME/bin/mvn" test                                                                    # full reactor
```
- Full reactor **BUILD SUCCESS**: messaging 12, security 27 (gồm MQTT 8 — ISSUE-02 còn nguyên), identity 51, order 22, health 3, common 13. 0 fail / 0 error.
- Không dependency cycle (messaging chỉ phụ thuộc Paho + validation; service → messaging một chiều).

## 8. Contract compatibility

- KHÔNG đổi MQTT topic / protobuf / JSON payload / public gRPC endpoint / DB schema (dùng trạng thái DEAD sẵn có — không Flyway mới).
- ISSUE-02 security giữ nguyên (verify vẫn gate; test security xanh).

## 9. Remaining risks

- Runtime smoke (QoS1 restart inflight, duplicate, gap, dir-not-writable fail-fast) chưa chạy — cần DB+broker, chưa dựng khi chưa được xác nhận. Compile + unit đã chứng minh wiring + invariant.
- `inventory` chưa đổi (TaskMqttSubscriber vốn đã ack-and-drop ở ISSUE-02); có thể áp classifier sau nếu muốn đồng nhất — DEFER.

## 10. Rollout

1. Tạo volume/dir persistence + permission trước khi bật FILE mode.
2. Rollout 1 instance Order consumer trước; theo dõi retry/DEAD/reconnect/duplicate/gap/projection-lag.
3. Ổn định rồi mới lan sang Identity/Inventory/Gateway theo mode phù hợp.

## 11. Rollback

- `git revert` commit; hoặc tạm chuyển consumer sang ephemeral bằng cấu hình (chỉ khi gap-recovery sẵn sàng).
- KHÔNG xoá file persistence khi còn message inflight chưa xử lý.

---
ISSUE-04 RESOLVED · ISSUE-05 RESOLVED (Option A) · ISSUE-09 RESOLVED · ISSUE-02 dependency = RESOLVED (giữ tương thích).
smartfarm-messaging: EXTRACT_SHARED hoàn tất (classifier + backoff + sanitizer + MQTT factory/settings).
