# 01 — Architecture & Graph Report (Báo cáo kiến trúc + đồ thị)

> Nguồn chân lý: `graphify-out/graph.json` (4509 nodes / 13727 edges), `.graphify_analysis.json` (gods/communities/cohesion/surprises). Mọi khẳng định đối chiếu source thật.

## 1. Kiến trúc thực tế (as-built)
Monorepo 13 module Maven: `libs/*` (proto, security, common-kernel), `services/*` (order, identity, inventory, livestock, health, finance, reporting), `platform/*` (readiness, farm-simulator), `apps/*` (gateway, web).

Giao tiếp:
- **Đồng bộ (gRPC)**: gateway → services; order → inventory/finance (saga steps). Xác thực qua `JwtServerInterceptor` (global interceptor, fail-closed) + `GrpcMethodPolicy` per-method.
- **Bất đồng bộ (MQTT)**: producers (outbox relays) → broker → consumers (gateway WS bridge, inventory task inbox, order read-model). Chi tiết ở `02-event-flow-report.md`.

Mỗi service theo lát cắt: `grpc/` (API) → `application/` (use-case) → `domain/` (entity + repo) → `outbox/`+`readmodel/` (integration). Đây là **layered + hexagonal-ish** rõ ràng (FACT, suy ra từ cây package đã liệt kê).

## 2. God-nodes & nút cổ chai (bottlenecks)
`graphify god-nodes` (FACT):
| # | Node | Degree | Module | Ghi chú |
|---|---|---|---|---|
| 1 | `OrderEntity` | 94 | order/domain | Aggregate trung tâm; fan-in rất cao |
| 2 | `OrderSagaStepEntity` | 67 | order/saga | Trạng thái step saga |
| 3 | `OrderSagaEntity` | 63 | order/saga | Aggregate saga |
| 4 | `UserAccountEntity` | 60 | identity/account | Aggregate user |
| 5 | `TaskEntity` | 53 | inventory | Ngoài scope sâu |
| 6 | `TenantMembershipEntity` | 53 | identity/tenant | RBAC membership |
| 7 | `InventoryRepository` | 53 | inventory | Ngoài scope sâu |
| 8 | `OrderOutboxEntity` | 50 | order/outbox | Snapshot event |
| 9 | `OrderSagaTransactionService` | 49 | order/saga | **Service orchestrating; nhiều @Transactional** |
| 10 | `OrderProjectionGapEntity` | 46 | order/readmodel/recovery | Gap recovery |

**Phân tích**: 7/10 god-node thuộc order-service ⇒ **order là trọng tâm kiến trúc và rủi ro**. `OrderSagaTransactionService` (deg 49) là nơi tập trung mọi transition saga với lock pessimistic (`findByIdForUpdate`, `findBySagaIdForUpdate`) — đúng chuẩn để tránh race, nhưng là single point of complexity (xem subgraph dưới). `OrderEntity` deg 94 là god-node dữ liệu điển hình: thay đổi schema/semantics `OrderEntity` lan rộng.

### Subgraph `OrderSagaTransactionService` (từ `graphify explain`, FACT)
- `<-- FarmOrderGrpcService` (imports/references) — API gọi `create/requestCancellation`.
- `<-- OrderSagaPersistentWorker` — worker gọi `claimBatch/claimStep/markForwardStepFailed/...`.
- `<-- OrderSagaCheckpointService`, `<-- OrderSagaAdministrationService`, `<-- OrderDraftService`.
- `--> OrderSagaJpaRepository`, `OrderSagaStepJpaRepository`, `OrderLineJpaRepository`, `OrderSagaProperties`.
⇒ Là **hub orchestration**; mọi đường ghi trạng thái saga đi qua đây (tốt cho tính nhất quán, nhưng cần test kỹ).

## 3. Vi phạm ranh giới / lệ thuộc chéo
Từ `.graphify_analysis.json.surprises` (FACT) + đọc source:
- `farm-simulator → common-kernel` và `farm-simulator → proto`: lệ thuộc chéo module (platform tool phụ thuộc lib dùng chung) — chấp nhận được cho công cụ, nhưng là **cross-module coupling** cần theo dõi.
- `GrpcCaller → TokenType` bắc cầu community **identity ↔ security**: identity dùng kiểu `TokenType` của security lib. Hợp lý (security là lib nền), nhưng cho thấy ranh giới identity/security khá chặt.
- `gateway → common-kernel/proto`: gateway phụ thuộc lib dùng chung — bình thường.

**Không phát hiện circular dependency ở tầng Maven** trong các scope đã đọc (INFERENCE — chưa chạy `graphify path` cho mọi cặp; dựa trên hướng phụ thuộc lib←service). UNKNOWN: chưa xác minh toàn bộ cặp.

## 4. Trùng lặp cấu trúc (duplication)
- **ISSUE-07**: hai class `SmartFarmGrpcSecurityAutoConfiguration` cùng tên ở `security.autoconfigure` và `security.grpc.autoconfigure`. Chỉ bản `grpc.autoconfigure` được liệt kê trong `AutoConfiguration.imports` ⇒ bản kia là dead code (FACT).
- **ISSUE-08**: hai đường saga (`OrderSagaOrchestrator` inline + persistent worker). Logic forward/compensation nhân đôi (FACT).

## 5. Orphan / dead code
- `MqttSecurityVerifier`, `MqttSecurityEnvelope`: class rỗng, 0 cạnh tham chiếu (ISSUE-02) — orphan + chức năng khuyết.
- `security.autoconfigure.SmartFarmGrpcSecurityAutoConfiguration`: không được nạp (ISSUE-07).
- `OrderSagaOrchestrator`: được inject vào `FarmOrderGrpcService` nhưng `run()` không được gọi trong gRPC surface (ISSUE-03/08) — dead trong luồng chính.

## 6. Diễn giải "surprises" của graph
Các surprise phản ánh **điểm nối liên-community có chủ đích**: identity cần `TokenType` của security để phân loại token; gateway/farm-simulator cần proto+kernel. Đây là **điểm tích hợp**, không phải lỗi — nhưng `GrpcCaller→TokenType` xác nhận rằng thay đổi `TokenType`/contract token sẽ ảnh hưởng cả hai community, cần phối hợp khi sửa ISSUE-11/12.

## 7. Kết luận kiến trúc
Kiến trúc tốt, phân lớp rõ, nhất quán giữa các service (cùng mẫu grpc/application/domain/outbox/readmodel). Rủi ro tập trung ở **order-service** (god-node + ISSUE-01/03/04/05) và **tầng MQTT security khuyết** (ISSUE-02). Khuyến nghị: hợp nhất saga path, dọn dead-config, và khép tầng bảo mật MQTT.
