# SmartFarm — Gateway API v1 (hiện trạng cho Frontend)

> Tài liệu này mô tả **toàn bộ REST API mà Frontend gọi được qua Gateway** (`http://localhost:8080` ở dev).
> Client CHỈ đi qua gateway; không service nội bộ nào expose ra ngoài. Cập nhật theo code ngày 2026-09-27.
> Mặt đọc: REST (mục 2–8) + **GraphQL** (mục 9). Realtime: **WebSocket** (mục 10). Schema các DB do **Flyway** quản lý (validate).
> Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) · OpenAPI JSON: `/v3/api-docs`.

## 1. Nguyên tắc chung

- **Ingress duy nhất**: mọi request đi vào gateway `:8080`. Identity/livestock/inventory/finance/order/reporting là gRPC nội bộ (loopback), gateway proxy vào.
- **Auth**: đăng nhập lấy `accessToken` (JWT, TTL 10 phút) + `refreshToken` (7 ngày). Gửi kèm mọi request bảo vệ: `Authorization: Bearer <accessToken>`.
- **Tenant**: lấy từ token (`tenant_id`), FE không cần gửi. Đa tenant sẵn sàng.
- **Idempotency**: các thao tác tạo (create task/order/item/receipt/export) yêu cầu header `Idempotency-Key: <chuỗi duy nhất>` — gửi lại cùng key trả về cùng kết quả, không tạo trùng.
- **Scope (RBAC)**: mỗi endpoint cần một quyền. Bảng scope ở mục 8. Super-admin (bootstrap đầu tiên) có `*` = mọi quyền.
- **Mã lỗi**: `400` sai input · `401` chưa/hết đăng nhập · `403` thiếu quyền · `404` không thấy · `409` sai trạng thái (vd hủy order đã hoàn thành) · `502/504` service nội bộ lỗi/timeout.

## 2. Auth & tài khoản (`/api/v1/auth`)

| Method | Path | Quyền | Body / ghi chú |
|---|---|---|---|
| POST | `/auth/register` | công khai | `{email, password}` — đăng ký (dev; prod có thể tắt) |
| POST | `/auth/login` | công khai | `{tenantId, email, password}` → `{accessToken, refreshToken}` |
| POST | `/auth/refresh` | công khai | `{refreshToken}` → cặp token mới |
| POST | `/auth/logout` | đăng nhập | `{refreshToken}` |
| GET | `/auth/me` | đăng nhập | → `{userId, tenantId, roles[], permissions[]}` |
| GET | `/auth/verify` | đăng nhập | → `{valid, subject, tenantId}` |
| POST | `/auth/password` | đăng nhập | `{oldPassword, newPassword}` |

## 3. Quản trị & phân quyền (`/api/v1/admin`) — cần `identity:admin`

| Method | Path | Ý nghĩa |
|---|---|---|
| GET | `/admin/roles` | Mọi role kèm permission — vd `{"SUPERADMIN":["*"],"FARM_OPERATOR":[...]}` |
| GET | `/admin/permissions` | Mọi scope hệ thống biết (vốn từ để cấp quyền) |
| GET | `/admin/users/{id}/roles` | Role + scope hiệu lực của 1 user |
| PUT | `/admin/users/{id}/roles/{role}` | Gán role cho user (không gán được SUPERADMIN/PLATFORM_ADMIN) |

Luồng phân quyền FE: liệt kê role → liệt kê scope → gán role cho user mới.

## 4. Livestock — Công việc (`/api/v1/livestock/tasks`)

Vòng đời: `CREATED → ASSIGNED → ACCEPTED → COMPLETED` (hoặc `CANCELLED`). Monitor tự phát cảnh báo quá hạn.

| Method | Path | Quyền | Body / query |
|---|---|---|---|
| POST | `/livestock/tasks` | `tasks:write` | header `Idempotency-Key`; `{farmId, title, type?, assigneeId?, dueAtEpochMs?}` |
| POST | `/livestock/tasks/{id}/assign` | `tasks:write` | `{assigneeId, acceptWindowSeconds?, reportWindowSeconds?}` — set deadline nhận/báo cáo |
| POST | `/livestock/tasks/{id}/accept` | `tasks:write` | (công nhân nhận việc) |
| POST | `/livestock/tasks/{id}/complete` | `tasks:write` | `{note?}` |
| POST | `/livestock/tasks/{id}/cancel` | `tasks:write` | `{reason?}` |
| GET | `/livestock/tasks` | `farm:read` | query `farmId` (bắt buộc), `status?`, `assigneeId?`, `limit?` |
| GET | `/livestock/tasks/{id}` | `farm:read` | → task đầy đủ mốc thời gian |

**Task trả về** (đầy đủ): `taskId, farmId, title, type, status, assigneeId, dueAt, assignedAt, acceptDeadlineAt, acceptedAt, reportDueAt, reportedAt, completedAt` (mốc = epoch ms).
`type`: `FEEDING | CLEANING | INSPECTION | MEDICATION | OTHER`.

**Giám sát quá hạn** (tự động, không cần FE gọi): monitor định kỳ phát event MQTT `task-accept-overdue.v1` (quá hạn nhận) và `task-report-overdue.v1` (quá hạn báo cáo). FE hiển thị cảnh báo qua kênh realtime (mục 7) hoặc badge dựa trên `acceptDeadlineAt`/`reportDueAt` so với hiện tại.

## 5. Livestock — Vật nuôi & Lịch (`/api/v1/livestock`)

| Method | Path | Quyền | Body / query |
|---|---|---|---|
| POST | `/livestock/animals` | `tasks:write` | `{farmId, tagCode, species?, barnId?, batchId?, birthDateEpochMs?}` — idempotent theo `tagCode` trong farm |
| GET | `/livestock/animals/{id}` | `farm:read` | → animal |
| GET | `/livestock/animals` | `farm:read` | query `farmId`, `batchId?`, `limit?` |
| POST | `/livestock/schedules` | `tasks:write` | `{farmId, title, type?, cronExpression, timeZone, assigneeId?, enabled?}` |
| GET | `/livestock/schedules` | `farm:read` | query `farmId`, `limit?` |

**Animal**: `animalId, farmId, tagCode, status(ANIMAL_STATUS_ACTIVE/SICK/SOLD/DECEASED), species, barnId, batchId, birthDate`.
**Schedule**: `scheduleId, farmId, title, type, cronExpression, timeZone, enabled, nextRunAt`. Cron 6 trường Spring (`giây phút giờ ngày tháng thứ`), `timeZone` là IANA (vd `Asia/Ho_Chi_Minh`). Sai cron/timezone → 400. **Generator tự sinh task** khi tới `nextRunAt` (không cần FE làm gì).

## 6. Inventory & Order saga (`/api/v1`)

Đặt hàng chạy **saga**: giữ kho → ghi chi phí → xác nhận; lỗi giữa chừng tự hoàn tác (compensation).

| Method | Path | Quyền | Body |
|---|---|---|---|
| POST | `/inventory/items` | `inventory:write` | header `Idempotency-Key`; `{sku, name, unit}` |
| POST | `/inventory/receipts` | `inventory:write` | header `Idempotency-Key`; `{itemId, farmId, warehouseId, quantity, unit}` (nạp kho) |
| POST | `/orders` | `orders:write` | header `Idempotency-Key`; **1 hoặc nhiều dòng** — xem dưới |
| GET | `/orders` | `orders:read` | query `farmId`, `status?`, `limit?` |
| GET | `/orders/{id}` | `orders:read` | → order (kèm `lines[]`) |
| POST | `/orders/{id}/cancel` | `orders:write` | `{reason?}` — hủy (compensate nếu đang giữ kho/chi phí); order COMPLETED → 409 |

**Body đặt hàng** (`POST /orders`) — hỗ trợ **nhiều dòng hàng**:
```json
{
  "farmId": "farm-1",
  "batchId": "wh-main",           // kho mặc định cấp đơn (fallback cho dòng không nêu kho)
  "currency": "VND",
  "lines": [
    { "itemId": "item-feed", "quantity": "10", "unit": "bag", "unitPriceMinor": 50000, "warehouseId": "wh-A" },
    { "itemId": "item-med",  "quantity": "2",  "unit": "box", "unitPriceMinor": 120000 }
  ]
}
```
- **Nhiều dòng**: mỗi dòng giữ (reserve) + xác nhận (commit) kho **độc lập**; lỗi bất kỳ dòng nào → hoàn tác toàn bộ (release mọi reservation đã giữ + reverse chi phí).
- **Warehouse-per-line**: mỗi dòng có thể lấy hàng từ **kho riêng** (`warehouseId`). Bỏ trống → dùng `batchId` cấp đơn (tương thích ngược với caller cũ gửi 1 kho).
- Tương thích cũ: vẫn chấp nhận payload 1 dòng phẳng (`{itemId, quantity, unit, unitPriceMinor, warehouseId}` không bọc `lines[]`).

**Order trả về** (đồng nhất cho `POST /orders`, `GET /orders/{id}`, phần tử của `GET /orders`, `POST /orders/{id}/cancel`):
```json
{
  "orderId": "…", "farmId": "farm-1", "batchId": "wh-main",
  "status": "ORDER_STATUS_COMPLETED",
  "totalMinor": 23000, "currency": "VND",
  "createdAt": "2026-09-27T16:24:18Z",
  "failureReason": "",
  "lines": [
    { "itemId": "item-feed", "quantity": "5", "unit": "kg", "unitPriceMinor": 3000, "currency": "VND", "warehouseId": "wh-A" },
    { "itemId": "item-med",  "quantity": "2", "unit": "kg", "unitPriceMinor": 4000, "currency": "VND", "warehouseId": "wh-main" }
  ]
}
```
- `warehouseId` rỗng ở dòng nào = dòng đó dùng `batchId` cấp đơn.
- `status`: `CREATED → STOCK_RESERVED → FINANCE_POSTED → COMPLETED`, hoặc `FAILED` / `CANCELLED`.
- `GET /orders` bọc thêm `{count, orders:[…]}`.
- Tiền theo **minor units** (VND: đồng; 5000 = 5.000đ). `totalMinor = Σ(quantity × unitPriceMinor)`.

**curl** — đặt đơn nhiều dòng, poll, hủy:
```bash
GW=http://localhost:8080
# đặt đơn 2 dòng (2 kho)
curl -sS -X POST "$GW/api/v1/orders" -H "Authorization: Bearer $ACCESS" \
  -H "Idempotency-Key: ord-$(uuidgen)" -H 'Content-Type: application/json' -d '{
    "farmId":"farm-1","batchId":"wh-main","currency":"VND",
    "lines":[
      {"itemId":"'$ITEM1'","quantity":"5","unit":"kg","currency":"VND","unitPriceMinor":3000,"warehouseId":"wh-A"},
      {"itemId":"'$ITEM2'","quantity":"2","unit":"kg","currency":"VND","unitPriceMinor":4000}
    ]}'
# đọc lại (kèm lines[])
curl -sS "$GW/api/v1/orders/$OID" -H "Authorization: Bearer $ACCESS"
# liệt kê theo farm + lọc trạng thái
curl -sS "$GW/api/v1/orders?farmId=farm-1&status=COMPLETED&limit=20" -H "Authorization: Bearer $ACCESS"
# hủy (order COMPLETED → 409)
curl -sS -X POST "$GW/api/v1/orders/$OID/cancel" -H "Authorization: Bearer $ACCESS" \
  -H 'Content-Type: application/json' -d '{"reason":"khách đổi ý"}'
```

**Read-model (CQRS)**: mỗi lần saga đổi trạng thái phát `OrderChanged` qua outbox → MQTT; order-service có consumer dựng **projection đọc nhanh** (`ord_order_view`) tách khỏi bảng saga. `GET /orders` / `orders(...)` GraphQL đọc dữ liệu này.

## 7. Reporting — Xuất báo cáo (`/api/v1/reports`)

Xuất **bất đồng bộ**: tạo job → worker sinh file → tải về.

| Method | Path | Quyền | Body / query |
|---|---|---|---|
| POST | `/reports` | `report:write` | header `Idempotency-Key`; `{farmId, type, format?, fromEpochMs?, toEpochMs?, templateId?}` |
| GET | `/reports/{id}` | `report:read` | → job (poll trạng thái) |
| GET | `/reports` | `report:read` | query `farmId`, `limit?` |
| GET | `/reports/{id}/download` | `report:read` | → `{url, contentType, expiresAt}` (chỉ khi COMPLETED, else 409) |

**Job**: `jobId, farmId, type, format, status, createdAt, completedAt?, errorCode?`.
`type`: `LIVESTOCK_TASKS | HEALTH | INVENTORY | BATCH_COST | CASH_FLOW`. `format`: `CSV | PDF | XLSX` (**render thật đa định dạng**: CSV, XLSX qua Apache POI, PDF qua OpenPDF).
`LIVESTOCK_TASKS` lấy **dữ liệu thật** từ livestock qua gRPC; các loại khác render bảng tóm tắt (nối read-model là bước sau).
`status`: `QUEUED → RUNNING → COMPLETED` / `FAILED`. FE: tạo job → poll `GET /reports/{id}` tới COMPLETED → gọi `/download`.

## 8. Bảng scope ↔ tính năng

| Scope | Cho phép |
|---|---|
| `farm:read` | Đọc task/animal/schedule |
| `tasks:write` | Tạo/giao/nhận/hoàn thành/hủy task, đăng ký animal, tạo schedule |
| `inventory:read` / `inventory:write` | Đọc / tạo item, nạp kho |
| `orders:read` / `orders:write` | Đọc / đặt & hủy đơn |
| `report:read` / `report:write` | Đọc job & tải / yêu cầu xuất báo cáo |
| `identity:admin` | Quản trị user/role |
| `identity:platform` | Quản trị nền tảng |
| `*` | Super-admin — mọi quyền (bootstrap đầu tiên) |

**Role sẵn**: `USER`(farm:read) · `FARM_OPERATOR`(vận hành: tasks/inventory/orders/report + farm:read) · `ADMIN`(identity:admin, farm:read) · `PLATFORM_ADMIN` · `SUPERADMIN`(*).

## 9. GraphQL (đọc linh hoạt) — `POST /graphql`

Ngoài REST, gateway mở **một mặt GraphQL chỉ-đọc** để FE lấy đúng field cần trong 1 round-trip (danh sách + chi tiết, chọn field). Đây thuần **tiện ích truy vấn** — dùng lại **cùng** gRPC service + per-service token + scope như REST; **không** thay thế REST cho ghi (create/assign/cancel vẫn dùng REST ở các mục trên).

- **Endpoint**: `POST /graphql` (cần `Authorization: Bearer <accessToken>` — tenant/actor lấy từ token).
- **GraphiQL** (thử tay, dev): [http://localhost:8080/graphiql](http://localhost:8080/graphiql).
- **Lưu ý dev**: resolver hiện chạy ở profile `dev` (tái dùng dev stub); prod wiring stub tương tự — xem v2 backlog.

**Query có sẵn**:

| Query | Scope | Trả về |
|---|---|---|
| `tasks(farmId!, status, assigneeId, limit)` | `farm:read` | `[Task!]!` |
| `task(id!)` | `farm:read` | `Task` |
| `orders(farmId!, status, limit)` | `orders:read` | `[Order!]!` |
| `order(id!)` | `orders:read` | `Order` (kèm `lines { itemId quantity unit unitPriceMinor warehouseId }`) |

Ví dụ (lấy đơn kèm dòng hàng + kho từng dòng):
```graphql
query {
  orders(farmId: "farm-1", status: "COMPLETED", limit: 20) {
    orderId status totalMinor batchId
    lines { itemId quantity unit unitPriceMinor warehouseId }
  }
}
```
Mốc thời gian trên `Task` là `Float` (epoch ms). `status` nhận cả dạng ngắn (`ASSIGNED`) lẫn đầy đủ (`TASK_STATUS_ASSIGNED`).

## 10. Sự kiện realtime (WebSocket) — cho FE

Gateway mở **WebSocket** đẩy domain event realtime cho FE, cầu nối từ MQTT nội bộ:

- **Endpoint**: `ws://localhost:8080/ws/events?token=<accessToken>[&farmId=<id>]`
- **Auth**: token JWT truyền qua query `?token=` (browser WS không gửi được header). Gateway verify token, chỉ đẩy event thuộc **tenant của token** (lọc thêm theo `farmId` nếu truyền).
- **Frame đầu**: `{"type":"connected","tenantId":"..."}` xác nhận đã xác thực; sau đó là các `DomainEvent` (JSON) liên tục.
- **Payload**: JSON của `DomainEvent` — chứa `metadata` (eventId, tenantId, farmId, aggregateId, occurredAt) + một trong các nhánh: `taskChanged`, `taskDeadlineBreached` (kind ACCEPT/REPORT_OVERDUE), `orderChanged`, ...

| Event (nhánh JSON) | Khi nào |
|---|---|
| `taskChanged` | Vòng đời task (created/assigned/accepted/completed/cancelled) |
| `taskDeadlineBreached` | Monitor phát khi quá hạn nhận/báo cáo |
| `orderChanged` | Mỗi lần saga đổi trạng thái đơn |

Ví dụ FE (JS):
```js
const ws = new WebSocket(`ws://localhost:8080/ws/events?token=${accessToken}&farmId=farm-1`);
ws.onmessage = (e) => { const evt = JSON.parse(e.data); /* cập nhật UI realtime */ };
```

## 11. Khởi động (dev) — cho người chạy thử

7 tiến trình, profile `dev`:
```bash
mvn -pl services/smartfarm-identity-service  spring-boot:run   # :8092 (loopback) gRPC nội bộ
mvn -pl services/smartfarm-livestock-service spring-boot:run   # :8081 / gRPC 9091
mvn -pl services/smartfarm-inventory-service spring-boot:run   # :8083 / gRPC 9093
mvn -pl services/smartfarm-finance-service   spring-boot:run   # :8084 / gRPC 9094
mvn -pl services/smartfarm-order-service     spring-boot:run   # :8085 / gRPC 9095
mvn -pl services/smartfarm-reporting-service spring-boot:run   # :8086 / gRPC 9096
mvn -pl apps/smartfarm-gateway               spring-boot:run   # :8080 INGRESS
```
Kiểm nhanh toàn bộ: `./scripts/release/v1/run-all.sh`.

**Điều kiện chạy đúng (tránh 2 lỗi thường gặp):**
1. **Build proto TRƯỚC khi khởi chạy sau khi đổi `.proto`.** Các RPC/field mới (vd `RecordExpense`, `OrderLine.warehouse_id`) sinh từ `libs/smartfarm-proto`. Nếu khởi chạy service trước khi proto được rebuild + cài vào `.m2`, service sẽ bind stub cũ → lỗi runtime `UNIMPLEMENTED: Method not found …` hoặc `400 Bad Request` cho payload mới, dù code service đã đúng. Luôn:
   ```bash
   mvn -o -pl libs/smartfarm-proto,services/smartfarm-finance-service,services/smartfarm-order-service,services/smartfarm-reporting-service,apps/smartfarm-gateway -am clean install -DskipTests
   ```
   rồi mới (re)start các tiến trình. Sau mỗi lần rebuild phải **restart lại process** — JVM đang chạy giữ class cũ.
2. **MQTT broker cho realtime + outbox.** `OrderChanged` (và event livestock) phát qua MQTT `:1883`. Không có broker → log `OrderOutboxRelay ... reason=MqttException, publish deferred` (an toàn, tự retry — không mất dữ liệu, nhưng event không rời hàng đợi) và WebSocket `/ws/events` mở nhưng không có event. Chạy broker trước:
   ```bash
   mosquitto -p 1883               # hoặc: docker run -p 1883:1883 eclipse-mosquitto
   ```
   Saga vẫn COMPLETED bình thường khi thiếu broker (outbox tách khỏi đường ghi); chỉ realtime/consumer bị hoãn.
3. **IntelliJ: bật "Delegate IDE build/run actions to Maven".** IntelliJ mặc định biên dịch bằng compiler riêng vào `out/`, **không chạy `protoc`** → stub gRPC (vd `FarmFinanceServiceGrpc` với `RecordExpense`, `OrderLine.warehouse_id`) mà IntelliJ nạp là bản cũ, gây `UNIMPLEMENTED`/`400` y như điểm 1 — dù `mvn` ở terminal build SUCCESS. Settings → Build, Execution, Deployment → Build Tools → Maven → Runner → tick **Delegate IDE build/run actions to Maven**, rồi Stop → Run lại. (Hoặc: Reload All Maven Projects → Rebuild Project sau mỗi `mvn install`.)
4. **Scheduler pool (đã cấu hình sẵn).** Các job `@Scheduled` (outbox relay, schedule generator, deadline monitor) mặc định chia **một** thread. Outbox relay publish MQTT **đồng bộ**; khi thiếu broker mỗi lần publish chờ connect-timeout rồi mới lỗi, làm **đói** schedule generator → cron không sinh task (test §4 livestock FAIL). Đã đặt `spring.task.scheduling.pool.size: 4` (dev, livestock + order) + `connectionTimeout=3s` cho MQTT để các job độc lập và fail-fast; generator vẫn chạy dù không có broker.
