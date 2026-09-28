# SmartFarm — Version 1 (nền tảng tính năng cơ bản)

> Tổng quan độ hoàn thiện để làm cơ sở **xây dựng giao diện**. Cập nhật 2026-09-28.
> Chi tiết API: [docs/v1/GATEWAY-API-V1.md](./docs/v1/GATEWAY-API-V1.md). Kiến trúc: [docs/shared/ARCHITECTURE.md](./docs/shared/ARCHITECTURE.md).
> Release notes: [docs/v1/RELEASE-NOTES-v1.md](./docs/v1/RELEASE-NOTES-v1.md).

## 1. Kiến trúc một dòng

Client → **Gateway (BFF, cửa duy nhất :8080)** → gRPC nội bộ tới các service → mỗi service DB riêng; sự kiện async qua **MQTT** (outbox → broker). Auth tập trung ở gateway + per-service token (zero-trust, có audit `actor_id` truy về người dùng thật). Mặt đọc cho FE: REST + **GraphQL** (`/graphql`); realtime qua **WebSocket** (`/ws/events`). 13 module Maven, Java 17, Spring Boot 4.1.1.

## 2. Ma trận hoàn thiện v1

| Miền | Trạng thái | Chi tiết |
|---|---|---|
| **Auth / Identity** | ✅ Hoàn thiện | login/refresh/logout/register, JWKS, RBAC scope, super-admin wildcard `*`, per-service token M2M, admin liệt kê/gán role. Identity khóa loopback (single-ingress). |
| **Livestock — Task** | ✅ Hoàn thiện | Vòng đời đầy đủ create/assign/accept/complete/cancel/list; **monitor quá hạn** nhận & báo cáo; phát event lifecycle qua outbox→MQTT. |
| **Livestock — Animal** | ✅ Cơ bản | Đăng ký (idempotent theo tag), get, list theo farm/batch. |
| **Livestock — Schedule** | ✅ Cơ bản | Tạo/list lịch cron (validate cron+timezone), **generator tự sinh task** theo cron. |
| **Inventory** | ✅ Hoàn thiện | Item, lot, balance, reserve/commit/release (xương sống saga), FIFO chọn lot. |
| **Finance** | ✅ Hoàn thiện | expense/income/payable/receivable/settle/**reverseExpense**, getTransaction/listTransactions, **getBatchCost/getCashFlow** (aggregate). Flyway migration. |
| **Order (Saga)** | ✅ Hoàn thiện | PlaceOrder orchestration reserve→expense→commit + compensation; List/Cancel; phát **OrderChanged** qua outbox. **Nhiều dòng hàng/đơn + warehouse-per-line** (mỗi dòng reserve/commit kho riêng). CQRS read-model (`ord_order_view`). |
| **Reporting** | ✅ Hoàn thiện | Export job bất đồng bộ; render **thật đa định dạng** CSV/XLSX(POI)/PDF(OpenPDF, font Unicode IDENTITY_H nhúng → render đúng tiếng Việt); LIVESTOCK_TASKS lấy dữ liệu thật qua gRPC. Worker log root-cause + lưu `errorCode` khi render lỗi. |
| **GraphQL (đọc)** | ✅ Có (dev) | `/graphql` — query `tasks/task/orders/order` (kèm `lines`), dùng lại gRPC+scope như REST. GraphiQL ở `/graphiql`. Prod wiring stub → v2. |
| **Realtime (WebSocket)** | ✅ Có | Gateway `ws://…/ws/events` đẩy DomainEvent (MQTT→WS), auth qua token, lọc theo tenant/farm. |
| **Schema (Flyway)** | ✅ Hoàn thiện | Flyway quản schema cho **identity/livestock/inventory/order/finance** (`validate` + baseline-on-migrate). Reporting dùng H2 in-mem (dev) nên giữ `ddl-auto`. |
| **Health** | ⚙️ Có domain | Vaccination/observation/alert (chưa nối vào FE v1). |
| **Readiness / Simulator** | ⚙️ Nền tảng | Health check, mô phỏng telemetry (vận hành, không phải màn hình nghiệp vụ). |

Chú thích: ✅ dùng được cho FE · ⚙️ có nền, chưa ưu tiên FE v1.

## 3. Nợ kỹ thuật còn lại (không chặn FE)

- **Outbox/generator single-instance (DEV)**: relay + schedule generator + export worker + WS MQTT subscriber chạy single-instance, poll cố định. An toàn dữ liệu nhờ idempotency-key, nhưng prod cần claim/distributed lock.
- **Reporting nguồn dữ liệu**: LIVESTOCK_TASKS render dữ liệu thật (gRPC); INVENTORY/HEALTH/BATCH_COST/CASH_FLOW render bảng tóm tắt — nối read-model từng service là bước mở rộng.
- **OrderChanged consumer bên ngoài**: order-service tự consume để dựng read-model; chưa có consumer liên-service (vd notification/analytics subscribe `order-changed/v1`).
- **Health/simulator RPC**: một số RPC health chưa implement; simulator là công cụ vận hành.

Đã dứt điểm trong v1 (không còn là nợ): multi-line order + warehouse-per-line, thống nhất compensation về `reverseExpense`, khóa cổng identity (loopback), Flyway cho 5 service Postgres, render báo cáo đa định dạng, WebSocket realtime.

## 4. Gợi ý màn hình Frontend (bám API v1)

| Màn hình | API dùng | Ghi chú UX |
|---|---|---|
| **Đăng nhập** | `POST /auth/login`, `/auth/refresh` | Lưu token, tự refresh trước hạn 10' |
| **Dashboard** | `/livestock/tasks?status=`, `/orders?status=` | Đếm task quá hạn (so `acceptDeadlineAt`/`reportDueAt` với now), đơn FAILED |
| **Quản lý công việc** | tasks CRUD + assign/accept/complete/cancel | Cột trạng thái + badge "quá hạn nhận/báo cáo"; nút theo trạng thái hiện tại |
| **Lịch định kỳ** | `/livestock/schedules` | Form cron + timezone; hiện `nextRunAt`; task tự sinh xuất hiện ở màn công việc |
| **Đàn vật nuôi** | `/livestock/animals` | List theo batch; đăng ký theo tag (chặn trùng tag) |
| **Kho** | `/inventory/items`, `/inventory/receipts` | Tạo item, nạp kho trước khi đặt hàng |
| **Đặt hàng** | `POST /orders`, `/orders`, `/orders/{id}/cancel` | Form **nhiều dòng hàng**, mỗi dòng chọn kho riêng (warehouse-per-line); hiện tiến trình saga (RESERVED→POSTED→COMPLETED); nút hủy ẩn khi COMPLETED |
| **Báo cáo** | `/reports` (tạo) → poll `/reports/{id}` → `/download` | Trạng thái QUEUED/RUNNING/COMPLETED; nút tải khi xong |
| **Quản trị** | `/admin/roles`, `/admin/permissions`, gán role | Chỉ role `identity:admin` |

**Nguyên tắc FE**: mọi call qua gateway `:8080`; gắn `Authorization: Bearer`; gửi `Idempotency-Key` cho thao tác tạo; tiền hiển thị chia theo minor units; thời gian là epoch ms (UTC) — format theo timezone người dùng.

## 5. Xác thực chất lượng

- Build: `mvn clean install` — 13 module BUILD SUCCESS.
- Test luồng nghiệp vụ: `scripts/release/v1/run-all.sh` (preflight + auth/rbac + livestock + order saga + reporting).
- Test lẻ: `scripts/livestock-lifecycle-test.sh`, `scripts/livestock-registry-test.sh`, `scripts/phase3-saga-curl-test.sh`, `scripts/phase2-curl-test.sh`.
- Mặt đọc GraphQL: thử tay ở GraphiQL `/graphiql` (cần đăng nhập lấy token).

## 6. Kết luận v1

Các **tính năng nghiệp vụ cơ bản đã sẵn sàng để dựng giao diện**: xác thực/phân quyền, quản lý công việc + giám sát quá hạn, vật nuôi, lịch tự sinh việc, kho, đặt hàng saga **nhiều dòng hàng + warehouse-per-line** có hoàn tác, xuất báo cáo đa định dạng. Mặt đọc cho FE có cả REST (đầy đủ) và GraphQL; realtime qua WebSocket; schema các DB chính do Flyway quản. Có bộ test luồng `scripts/release/v1/` để hồi quy. Phần còn lại là **backlog v2** dưới đây — đã đánh giá là chưa cần cho v1, không phải nợ chặn.

## 7. Backlog v2 (đánh giá: chưa cần cho v1)

Ghi lại các mở rộng đã cân nhắc nhưng **cố ý hoãn** để giữ v1 gọn, ổn định cho việc dựng FE. Không mục nào là nợ chặn.

- **Lombok** *(đã đánh giá — KHÔNG thêm cho v1)*: lợi ích là gọn code (`@Slf4j` bỏ dòng logger lặp, `@Getter/@Builder/@RequiredArgsConstructor`). Rủi ro không đáng đổi ngay trước đóng gói: chạm ~20 entity/service (diện rộng, dễ regression), `@Data/@EqualsAndHashCode` trên `@Entity` JPA là bẫy đã biết (equals/hashCode + lazy-loading), và thêm một annotation processor vào build đang xanh. **Lộ trình đề xuất**: nếu làm, bắt đầu bằng `@Slf4j` (chỉ logger, rủi ro thấp nhất) từng module có test; entity để sau cùng, không dùng `@Data` trên entity.
- **GraphQL prod wiring**: resolver hiện `@Profile("dev")` vì tái dùng dev gRPC stub. Prod cần cấu hình stub prod tương tự (không đổi schema).
- **GraphQL mutations**: v1 chỉ đọc; ghi vẫn qua REST. Cân nhắc mutation nếu FE muốn một mặt duy nhất.
- **Multi-instance hardening**: distributed lock/claim cho outbox relay, schedule generator, export worker, WS MQTT subscriber (hiện single-instance an toàn nhờ idempotency-key).
- **Reporting read-model thật cho các loại còn lại**: INVENTORY/HEALTH/BATCH_COST/CASH_FLOW hiện render tóm tắt; nối gRPC/read-model từng service để có dữ liệu thật.
- **OrderChanged consumer liên-service**: notification/analytics subscribe `order-changed/v1`.
- **Reporting Flyway**: reporting dùng H2 in-mem (dev); khi có DB bền vững thì thêm Flyway như các service khác.
- **Health/Simulator**: hoàn thiện RPC health còn thiếu + đưa vào FE nếu cần.
