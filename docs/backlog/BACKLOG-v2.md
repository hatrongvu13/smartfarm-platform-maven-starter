# SmartFarm — Backlog v2

> Kế hoạch phiên bản **v2**: hoàn thiện các hạng mục đã cố ý hoãn ở v1 (không chặn FE), **cộng** phần backend cần thiết
> cho **mô hình trang trại 3D** (định vị kho + khu vực + giao task theo vị trí). Mỗi mục ghi: mục tiêu, phạm vi, tiêu
> chí hoàn thành (DoD).

## Nhóm 1 — Nền tảng cho FE 3D (MỚI, ưu tiên cao nếu làm 3D)

### 1.1 Không gian kho/khu vực (warehouse & zone spatial)
- **Mục tiêu**: thêm toạ độ/không gian cho warehouse và zone để FE 3D định vị được.
- **Phạm vi**: proto `inventory` (hoặc service mới `layout`) thêm `Warehouse{ id, name, position{x,y,z}, rotationY }`,
  `Zone{ id, warehouseId, label, position, itemIds[] }`; RPC `GetFarmLayout(farmId)`, `UpsertWarehouse`, `UpsertZone`;
  gateway REST `GET/POST /api/v1/farm/{farmId}/layout`, scope đọc `farm:read`, ghi `inventory:write`.
- **DoD**: FE lấy `Farm3DLayout` thật; CRUD vị trí kho/zone; migration Flyway; test contract.

### 1.2 Task gắn vị trí (task-location)
- **Mục tiêu**: nhân công biết đi tới kho/khu nào khi nhận task.
- **Phạm vi**: proto `livestock` thêm field backward-compatible `Task.warehouse_id`, `Task.zone_id`; assign nhận thêm
  `warehouseId/zoneId`; list/get trả kèm; event `taskChanged` mang vị trí.
- **DoD**: FE assign task tại một zone; task detail hiển thị vị trí; realtime cập nhật.

### 1.3 Reserve/commit theo vị trí trực quan
- **Mục tiêu**: khi order saga reserve/commit theo `warehouseId` từng dòng, FE 3D highlight kho đang trừ tồn.
- **Phạm vi**: đảm bảo `orderChanged` mang đủ `warehouseId` từng line (đã có ở `OrderLine.warehouseId`); FE map sang 3D.
- **DoD**: highlight realtime kho theo tiến trình saga.

## Nhóm 2 — Read-model & dữ liệu báo cáo thật

### 2.1 Reporting read-model cho các loại còn lại
- **Mục tiêu**: INVENTORY/HEALTH/BATCH_COST/CASH_FLOW render **dữ liệu thật** (v1 mới render bảng tóm tắt).
- **Phạm vi**: reporting pull qua gRPC/read-model từng service (finance đã có `getBatchCost/getCashFlow`).
- **DoD**: 5 loại report đều có dữ liệu thật; test render.

### 2.2 OrderChanged consumer liên-service
- **Mục tiêu**: service khác (notification/analytics) subscribe `order-changed/v1`.
- **DoD**: có ít nhất một consumer ngoài order-service tiêu thụ event idempotent.

### 2.3 Reporting Flyway
- **Mục tiêu**: reporting dùng DB bền vững → thêm Flyway như 5 service kia (v1 dùng H2 in-mem).
- **DoD**: baseline `V1__reporting_baseline.sql`, `validate` + baseline-on-migrate.

## Nhóm 3 — Mặt đọc GraphQL

> **Chiến lược**: GraphQL là **mặt đọc hợp nhất** — mọi truy vấn đọc/tổng hợp cho FE đi qua đây để **không sinh thêm
> REST endpoint nhỏ lẻ**. Giữ nguyên tắc read-convenience: dùng lại gRPC stub + per-service token + scope y REST,
> **không** thêm proto/entity/saga. Ghi cho FE là REST (v1).

### 3.0 Đã làm (v1, dev): query tổng hợp `dashboard`, `warehouseInventory`, `batchCost`
- `dashboard(farmId, recentLimit)` gộp counts task/order + overdue + failed trong 1 round-trip, tính server-side.
- `warehouseInventory(itemId, warehouseId)` → tồn kho tại một điểm (InventoryService.GetStockBalance).
- `batchCost(farmId, batchId)` → chi phí theo lô (FarmFinanceService.GetBatchCost).
- `cashFlow(farmId, fromEpochMs, toEpochMs)` → dòng tiền theo khoảng (FarmFinanceService.GetCashFlow).
- `lowStock(farmId, limit)` → tồn kho dưới ngưỡng (InventoryService.ListLowStock **đã implement**: item có
  `reorder_threshold` > 0, so available theo (item,warehouse) trong farm; Flyway V2 thêm cột; đặt threshold khi tạo item).
- Tất cả read-only, dùng lại gRPC + per-service token + scope; gateway đã có finance/inventory stub. Đã có ở
  schema + `FarmGraphQlController`. Test: `scripts/release/v1/60-graphql.sh`.

> **Mặt đọc GraphQL v1 đã ĐÓNG.** Các mục dưới (§3.1) là **tiến trình cải thiện tiếp theo (v2)** — không thuộc v1.

### 3.1 Tiến trình mở rộng GraphQL đọc (v2) — tối ưu, tránh endpoint sprawl
Thứ tự đề xuất, mỗi bước là read-only dùng lại gRPC:
1. **Phân trang chuẩn (Relay-style)**: `TaskConnection`/`OrderConnection` với `edges/pageInfo{endCursor,hasNextPage}`
   thay `limit` thô — cho list lớn. DoD: cursor ổn định, FE cuộn vô hạn.
2. **Bộ lọc & sắp xếp giàu hơn**: `tasks(filter:{status[],assigneeId,overdue:Boolean,dueBetween}, sort)` —
   gộp nhiều nhu cầu lọc vào một query thay vì nhiều endpoint.
3. **Tổng hợp nghiệp vụ còn lại**: `traceLot(lotId)` (InventoryService.TraceLot), `listTransactions` — mở rộng tương tự.
4. **DataLoader / batch resolver**: gộp N+1 (vd order → lines, task → assignee) bằng batch loader để 1 query nhiều
   entity không nổ nhiều gRPC call.
5. **Field-level scope**: mỗi field nhạy cảm gắn `@PreAuthorize` riêng (vd tiền chỉ ai có `report:read`), để một query
   tổng hợp vẫn tôn trọng RBAC theo field.
6. **Persisted queries + depth/complexity limit**: chặn query quá sâu/đắt; cache query hash cho FE.
- **DoD chung**: mọi bổ sung là read-only, không đổi schema ghi; có test scope + phân trang; GraphiQL cập nhật ví dụ.

### 3.2 GraphQL prod wiring
- **Mục tiêu**: resolver hiện `@Profile("dev")` (tái dùng dev stub) → cấu hình stub prod tương tự, **không đổi schema**.
- **DoD**: `/graphql` chạy ở prod với per-service token + scope như REST.

### 3.3 GraphQL mutations (tùy chọn)
- **Mục tiêu**: nếu FE muốn một mặt duy nhất, thêm mutation ghi (v1 ghi vẫn qua REST).
- **DoD**: mutation create/assign/... dùng lại gRPC + scope; không phá REST.

## Nhóm 4 — Hardening production

### 4.1 Multi-instance (distributed lock/claim)
- **Mục tiêu**: outbox relay, schedule generator, export worker, WS MQTT subscriber chạy được **nhiều instance**
  (v1 single-instance an toàn nhờ idempotency-key nhưng poll cố định).
- **DoD**: distributed lock/claim (vd DB advisory lock/Redis) + test không double-process.

### 4.2 TLS/mTLS gRPC transport
- **Mục tiêu**: bind `smartfarm.grpc.tls` vào Netty server/channel (v1 mới là feature-switch, chưa bind); nội bộ nâng mTLS.
- **DoD**: server/channel TLS thật + test; private key qua secret manager, không commit.

### 4.3 Health/Simulator hoàn thiện
- **Mục tiêu**: implement nốt RPC health còn thiếu; simulator publish MQTT thật (hiện trả payload mô phỏng).
- **DoD**: health domain nối FE nếu cần; simulator publish telemetry thật.

## Nhóm 5 — Chất lượng mã (đánh giá)

### 5.1 Lombok *(đã đánh giá — KHÔNG thêm ở v1; cân nhắc v2)*
- **Lợi**: gọn code (`@Slf4j`, `@Getter/@Builder/@RequiredArgsConstructor`).
- **Rủi ro**: chạm ~20 entity/service (diện rộng), `@Data/@EqualsAndHashCode` trên `@Entity` JPA là bẫy đã biết
  (equals/hashCode + lazy-loading), thêm annotation processor vào build đang xanh.
- **Lộ trình nếu làm**: bắt đầu `@Slf4j` (rủi ro thấp) từng module có test; entity để sau, **không** dùng `@Data` trên entity.

---

## Thứ tự đề xuất
1. Nhóm 1 (nếu quyết làm 3D) — mở đường cho FE-M2/M3.
2. Nhóm 2.3 + 4.2 (Flyway reporting, TLS) — hardening nền.
3. Nhóm 2.1/2.2, 3.1 — dữ liệu thật + GraphQL prod.
4. Nhóm 4.1, 4.3, 3.2, 5.1 — theo nhu cầu vận hành/quy mô.
