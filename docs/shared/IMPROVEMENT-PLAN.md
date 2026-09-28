# SmartFarm — Đánh giá & Kế hoạch cải thiện

> Nguồn phân tích: graph code do `graphify` sinh (961 nodes / 2115 edges / 57 communities, 63 file Java) + đọc trực tiếp mã nguồn ngày 2026-09-26. Đây là tài liệu kế hoạch — chưa đụng code.

## 1. Hiện trạng giao tiếp (đo từ graph + grep)

| Module | REST ra ngoài | gRPC (nội bộ) | MQTT |
|---|---|---|---|
| **gateway** (app) | ✅ `WhoAmIController`, `LivestockDevController` (`dev&!prod`) | client stub | — |
| **identity-service** | ⚠️ `AuthController` + `AdminController` **KHÔNG giới hạn profile** — expose thật | `IdentityDirectoryService` (GetPrincipal/CheckPermission) | — |
| livestock-service | — | `LivestockGrpcService` | outbox → MQTT publisher |
| health-service | — | `HealthGrpcService` | Paho publisher |
| inventory-service | `InboxDevController` (`dev&!prod`, chỉ `/internal/dev`) | `InventoryGrpcService` | MQTT inbox (subscriber) |
| finance-service | — | contract-only (`FarmFinanceService`) | — |
| reporting-service | — | contract-only (`ReportingService`) | — |
| readiness (platform) | health endpoint | `ReadinessGrpcService` | — |
| farm-simulator (platform) | `SimulationController` (dev) | — | Paho + observer |

**Kết luận cửa ngõ:** dự án **đã gần đúng** mục tiêu "chỉ gateway ra ngoài". Điểm lệch DUY NHẤT: **identity-service đang expose REST production** (login/register/refresh/admin) trực tiếp, không qua gateway.

## 2. Thiếu sót & phần chưa tốt

### A. Cửa ngõ / security
1. **identity REST expose thẳng** — vi phạm nguyên tắc single-ingress. Cần đưa auth flow ra sau gateway, service chỉ giữ phần **xác thực nội bộ** (JWKS cho service khác verify, gRPC `CheckPermission`).
2. `LivestockDevController` forward **user token** xuống gRPC (comment tự nhận "DEV ONLY, tạm thời") — chưa có **per-service audience token**. Nợ kỹ thuật đã ghi trong code.
3. Dev controller dùng `@Profile("dev & !prod")` nhưng không có rào **network-layer** — README/doc chưa nêu rõ.

### B. Saga / nhất quán dữ liệu
4. `order.proto` khai `FarmOrderService` + `OrderChanged` event **nhưng chưa có service nào implement** — Saga owner còn trống. Đây là chỗ để "triển khai thêm Saga".
5. Outbox pattern **mới chỉ có ở livestock + health** (một chiều publish). Inventory có inbox. **Chưa có orchestrator** điều phối chuỗi (đặt hàng → giữ kho → ghi nợ → xác nhận), chưa có **compensating action**.
6. `OutboxRelay` tự nhận "Single-instance DEV relay" — chưa an toàn multi-instance (không có lock/claim), poll cố định 2s.

### C. Domain task — thiếu cơ chế giám sát (chính là thứ bạn muốn)
7. `TaskEntity` chỉ có `status` phẳng (`CREATED`…) + `createdAt`. **Thiếu**: `assignedAt`, `acceptedAt`, `dueAt`, `acceptDeadlineAt`, loại công việc (kiểm kho / tiêm phòng / kiểm hàng nhập). Không lưu được "đã nhận chưa", "hạn báo cáo".
8. **Không có scheduler giám sát**: không có job nào kiểm "sau 5–10' task đã có công nhân nhận chưa" hay "quá hạn báo cáo". `@Scheduled` duy nhất hiện có là OutboxRelay.
9. Task không phát sự kiện vòng đời đủ mịn (chỉ `task-created.v1`). Thiếu `task-assigned`, `task-accepted`, `task-overdue`, `task-report-due`.

### D. Module rỗng
10. finance & reporting **gần như rỗng** (contract-only) — graph không thấy repository/service impl. Không phải lỗi nhưng là "chưa hoàn thiện".

## 3. Kế hoạch cải thiện — theo phase, có kiểm chứng

### Phase 1 — Siết cửa ngõ (chỉ gateway ra ngoài)
- **Giữ ở identity (nội bộ):** `/.well-known/jwks.json` (service khác verify JWT), gRPC `IdentityDirectoryService`, cấp/rotate token.
- **Chuyển ra sau gateway:** route `POST /api/v1/auth/**` và `/api/v1/admin/**` → gateway proxy tới identity (qua gRPC hoặc nội bộ HTTP không expose).
- **Bỏ expose trực tiếp:** identity chỉ bind cổng nội bộ; các REST business khác đã dev-only — giữ nguyên.
- Kiểm chứng: `mvn verify`; xác nhận không còn `@RestController` production nào ngoài gateway + JWKS.

### Phase 2 — Chuẩn hoá gRPC + MQTT làm kênh liên-service duy nhất
- Mọi lệnh (command) service↔service: **gRPC**. Mọi sự kiện (event) bất đồng bộ: **MQTT** (outbox → broker → inbox).
- Bổ sung outbox/inbox còn thiếu ở inventory/finance/reporting theo cùng mẫu livestock.
- Thay **user-token forwarding** bằng **per-service token** (audience đúng service đích).

### Phase 3 — Saga orchestration (implement `FarmOrderService`)
- Tạo **order-service** (hoặc owner trong 1 module) implement `FarmOrderService`.
- Saga "Nhập kho có phát sinh chi phí": `PlaceOrder` → (inventory) `ReceiveStock`/`ReserveStock` → (finance) `RecordPayable` → xác nhận. Mỗi bước phát `DomainEvent`; lỗi giữa chừng → **compensating** (`ReleaseReservation`, huỷ payable).
- Orchestrator lưu **saga state** (JPA) + timeout mỗi bước.

### Phase 4 — Hệ kiểm tra / giám sát task (yêu cầu trọng tâm)
Mô hình: mỗi task có **loại** + **các mốc thời gian** + **scheduler quét**.
- **Mở rộng `TaskEntity`**: `taskType` (STOCK_CHECK / VACCINATION / GOODS_INBOUND_CHECK…), `assignedAt`, `acceptDeadlineAt` (giao + 5–10'), `reportDueAt` (giao + thời hạn theo loại), `acceptedAt`, `reportedAt`.
- **Proto**: thêm `AssignTask` set deadline, event `task-accept-overdue.v1`, `task-report-overdue.v1`.
- **`TaskDeadlineMonitor` (`@Scheduled`)**: quét định kỳ →
  - task `ASSIGNED` mà `now > acceptDeadlineAt` và chưa `acceptedAt` → phát `task-accept-overdue` (nhắc/giao lại).
  - task đã nhận mà `now > reportDueAt` và chưa `reportedAt` → phát `task-report-overdue` (yêu cầu báo cáo).
- Sự kiện đi qua **outbox → MQTT**; simulator/health/inventory subscribe để mô phỏng công nhân nhận & báo cáo.
- Bảng cấu hình thời hạn theo loại việc (kiểm kho X phút, tiêm phòng Y giờ…).

### Phase 5 — Hoàn thiện finance/reporting (tùy chọn, sau)
- Implement repository + gRPC thật cho finance (payable/receivable phục vụ Saga) và reporting (export job).

## 4. Thứ tự đề xuất
Phase 4 (giám sát task) và Phase 1 (siết cửa ngõ) là 2 phần bạn nêu rõ nhất → làm trước. Phase 3 (Saga) xây trên nền outbox/inventory đã có. Phase 2 & 5 là chuẩn hoá/mở rộng nền.
