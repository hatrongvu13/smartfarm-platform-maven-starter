# SmartFarm — Frontend Requirements v1 (bản require chuẩn, tự chứa)

> Tài liệu **yêu cầu Frontend v1** dùng để lập kế hoạch xây dựng FE. **Tự chứa toàn bộ thông tin** — không tham chiếu
> tài liệu khác. Mỗi màn hình được mô tả theo cấu trúc: **Màn hình → Tính năng → DTO cần xây → Validate & bắt lỗi →
> Flow request & chuyển màn hình**. Kèm bảng chia quyền (RBAC) đầy đủ.
>
> Bản quyền dữ liệu: mọi request đi qua **một cổng duy nhất** `http://localhost:8080` (dev). Kèm `Authorization: Bearer <accessToken>`.
> Tenant lấy từ token — FE **không** gửi tenant. Thao tác tạo (create) cần header `Idempotency-Key: <chuỗi duy nhất>`.
> Tiền theo **minor units** (VND: 1 đơn vị = 1 đồng). Mốc thời gian là **epoch milliseconds (UTC)**.

---

## A. Nguyên tắc chung toàn hệ thống

### A.1 Xác thực & phiên
- `accessToken` JWT sống **10 phút**; `refreshToken` sống **7 ngày**.
- FE lưu token an toàn (khuyến nghị: access token trong bộ nhớ, refresh token trong `httpOnly` cookie nếu có BFF phụ,
  hoặc `localStorage` cho dev). **Tự refresh** khi access token còn ~2 phút: gọi `POST /api/v1/auth/refresh`.
- Mọi request bảo vệ gắn `Authorization: Bearer <accessToken>`. Thiếu/hết hạn → `401` → điều hướng về **Đăng nhập**
  (thử refresh một lần trước khi bắt đăng nhập lại).

### A.2 Mã lỗi HTTP thống nhất (mọi endpoint)
| Mã | Ý nghĩa | FE xử lý |
|---|---|---|
| `400` | Input sai / thiếu field bắt buộc / body multi-line thiếu field | Hiện lỗi field, không rời màn hình |
| `401` | Chưa/hết đăng nhập | Thử refresh 1 lần → nếu vẫn 401, về Đăng nhập |
| `403` | Thiếu scope | Ẩn/disable hành động; hiện "Bạn không có quyền" |
| `404` | Không tìm thấy tài nguyên | Toast "Không tồn tại", quay lại danh sách |
| `409` | Sai trạng thái (vd hủy order đã COMPLETED, tag animal trùng) | Toast giải thích, refresh dữ liệu |
| `502` | Service nội bộ lỗi/không sẵn sàng (`UNAVAILABLE`/`UNIMPLEMENTED`) | Toast "Hệ thống bận", cho retry |
| `504` | Service nội bộ timeout (deadline) | Toast "Quá thời gian", cho retry |

> Backend map gRPC→HTTP: `INVALID_ARGUMENT→400`, `NOT_FOUND→404`, `FAILED_PRECONDITION/ALREADY_EXISTS→409`,
> `UNAUTHENTICATED→401`, `PERMISSION_DENIED→403`, `DEADLINE_EXCEEDED→504`, `UNAVAILABLE/UNIMPLEMENTED→502`.
> Thân lỗi dạng Spring `ResponseStatusException`: `{ "status", "error", "message" }` — FE đọc `message` để hiển thị.

### A.3 Idempotency
- Các POST tạo (`/livestock/tasks`, `/livestock/animals`? *(không cần)*, `/inventory/items`, `/inventory/receipts`,
  `/orders`, `/reports`) **bắt buộc** header `Idempotency-Key`. FE sinh 1 UUID **cho mỗi lần bấm submit**, gửi lại
  cùng key khi retry để không tạo trùng. (Register animal / create schedule / các lifecycle POST **không** cần key.)

### A.4 Realtime (WebSocket)
- Endpoint: `ws://localhost:8080/ws/events?token=<accessToken>[&farmId=<id>]` (token qua query vì browser WS không gửi header).
- Frame đầu: `{"type":"connected","tenantId":"..."}`. Sau đó là các `DomainEvent` JSON.
- Nhánh sự kiện FE quan tâm: `taskChanged` (vòng đời task), `taskDeadlineBreached` (quá hạn nhận/báo cáo, có `kind`
  ACCEPT_OVERDUE/REPORT_OVERDUE), `orderChanged` (mỗi lần saga đổi trạng thái).
- FE mở 1 WS cho phiên; reconnect với backoff khi rớt; lọc theo `farmId` đang xem.

---

## B. Chia quyền (RBAC) — bảng đầy đủ

### B.1 Scope ↔ hành động
| Scope | Cho phép |
|---|---|
| `farm:read` | Đọc task/animal/schedule; whoami `/api/v1/me`; GraphQL đọc |
| `tasks:write` | Tạo/giao/nhận/hoàn thành/hủy task; đăng ký animal; tạo schedule |
| `inventory:read` | Đọc kho (v1: chưa có endpoint đọc riêng — dự phòng) |
| `inventory:write` | Tạo item; nạp kho (receipt) |
| `orders:read` | Đọc/list đơn |
| `orders:write` | Đặt & hủy đơn |
| `report:read` | Đọc job & tải báo cáo |
| `report:write` | Yêu cầu xuất báo cáo |
| `identity:admin` | Quản trị user/role/permission |
| `identity:platform` | Quản trị nền tảng |
| `*` | Super-admin — mọi quyền (tài khoản bootstrap đầu tiên) |

### B.2 Role sẵn có (mặc định)
| Role | Scope |
|---|---|
| `USER` | `farm:read` |
| `FARM_OPERATOR` | `farm:read`, `tasks:write`, `inventory:read`, `inventory:write`, `orders:read`, `orders:write`, `report:read`, `report:write` |
| `ADMIN` | `identity:admin`, `farm:read` |
| `PLATFORM_ADMIN` | `identity:platform` (+ nền tảng) |
| `SUPERADMIN` | `*` |

### B.3 Ma trận Màn hình × Scope tối thiểu (điều khiển hiển thị/ẩn ở FE)
| Màn hình | Scope xem | Scope hành động |
|---|---|---|
| Đăng nhập / Đăng ký | công khai | — |
| Dashboard | `farm:read` | — |
| Quản lý công việc | `farm:read` | `tasks:write` |
| Lịch định kỳ | `farm:read` | `tasks:write` |
| Đàn vật nuôi | `farm:read` | `tasks:write` |
| Kho (tạo item / nạp kho) | `farm:read` | `inventory:write` |
| Đặt hàng | `orders:read` | `orders:write` |
| Báo cáo | `report:read` | `report:write` |
| Quản trị (user/role) | `identity:admin` | `identity:admin` |

> FE đọc scope hiệu lực từ `GET /api/v1/auth/me` (`permissions[]`); ẩn nút thao tác khi thiếu scope; backend vẫn
> enforce `@PreAuthorize` nên FE-hiding chỉ là UX, không phải bảo mật.

---

## C. Màn hình chi tiết

Ký hiệu: **[W]** = write (đổi dữ liệu), **[R]** = read.

---

### C.1 Màn hình ĐĂNG NHẬP

**Tính năng**: đăng nhập, (tùy chọn) đăng ký dev, tự refresh, đăng xuất, đổi mật khẩu.

**DTO gửi/nhận**
```ts
// POST /api/v1/auth/login  (công khai)
interface LoginRequest  { tenantId: string; email: string; password: string; }
interface LoginResponse { accessToken: string; refreshToken: string; }

// POST /api/v1/auth/refresh (công khai)
interface RefreshRequest  { refreshToken: string; }
// -> LoginResponse (cặp token mới)

// POST /api/v1/auth/logout  (cần token)   body: { refreshToken: string }
// POST /api/v1/auth/register (công khai, dev) body: { email: string; password: string }
// POST /api/v1/auth/password (cần token)   body: { oldPassword: string; newPassword: string }

// GET /api/v1/auth/me (cần token)
interface MeResponse { userId: string; tenantId: string; roles: string[]; permissions: string[]; }
```

**Validate & bắt lỗi (FE)**
- `tenantId`, `email`, `password` non-empty; email đúng định dạng; password ≥ 8 ký tự (đăng ký).
- `401` từ login → "Sai tài khoản hoặc mật khẩu" (không lộ field nào sai). Tài khoản khoá sau **5 lần** sai
  (backend `max-failures=5`, `lock-duration=15m`) → hiện "Tài khoản tạm khoá 15 phút".
- Không log token ra console.

**Flow request & chuyển màn hình**
1. Nhập → `POST /auth/login` → lưu token → gọi `GET /auth/me` để lấy `permissions[]` (quyết định menu).
2. Thành công → chuyển **Dashboard**.
3. Nền: timer refresh trước hạn 2' → `POST /auth/refresh`; refresh lỗi → về Đăng nhập.
4. Đăng xuất → `POST /auth/logout` → xoá token → về Đăng nhập.

**curl thật**
```bash
GW=http://localhost:8080
# login -> lấy token
RESP=$(curl -sS -X POST "$GW/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d '{"tenantId":"farm-demo","email":"admin@example.test","password":"replace-with-a-long-random-password"}')
ACCESS=$(printf '%s' "$RESP" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
REFRESH=$(printf '%s' "$RESP" | sed -n 's/.*"refreshToken":"\([^"]*\)".*/\1/p')
# me (roles + permissions -> quyết định menu)
curl -sS "$GW/api/v1/auth/me" -H "Authorization: Bearer $ACCESS"
# refresh
curl -sS -X POST "$GW/api/v1/auth/refresh" -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$REFRESH\"}"
# logout
curl -sS -X POST "$GW/api/v1/auth/logout" -H "Authorization: Bearer $ACCESS" \
  -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$REFRESH\"}"
```

---

### C.2 Màn hình DASHBOARD **[R]** — scope `farm:read` + `orders:read`

**Tính năng**: tổng quan số task theo trạng thái, task **quá hạn**, đơn **FAILED** gần đây; entry point sang các màn hình.

**DTO — dùng GraphQL `dashboard` (tổng hợp 1 round-trip, KHÔNG gọi nhiều list rồi tự đếm)**
```ts
// POST /graphql   scope farm:read + orders:read
// query { dashboard(farmId, recentLimit) { ... } }
interface DashboardResult {
  farmId: string;
  generatedAt: number;                 // epoch ms — mốc để xác định quá hạn (tính server-side)
  tasks: {
    total: number; created: number; assigned: number; accepted: number;
    completed: number; cancelled: number;
    overdueAccept: number;             // ASSIGNED đã quá acceptDeadlineAt
    overdueReport: number;             // ACCEPTED đã quá reportDueAt
  };
  orders: {
    total: number; created: number; stockReserved: number; financePosted: number;
    completed: number; failed: number; cancelled: number;
  };
  overdueTasks: Task[];                // rút gọn (recentLimit, mặc định 10) — dùng type Task ở C.3
  failedOrders: Order[];              // rút gọn — dùng type Order ở C.7
}
```

**Validate & bắt lỗi**
- `farmId` **bắt buộc** (multi-farm) — mặc định farm đầu tiên user có.
- Thiếu `orders:read` → **403** cho cả query (dashboard gộp task+order). Nếu user chỉ có `farm:read`, FE fallback về
  gọi riêng `tasks(...)` GraphQL (chỉ cần `farm:read`) và ẩn phần đơn.
- Lỗi query → khối "không tải được", nút thử lại.

**Flow request & chuyển màn hình**
1. Vào Dashboard → **một** query `dashboard(farmId, recentLimit)` → có ngay counts + overdue + failed (server đã tính,
   `generatedAt` là mốc quá hạn — không cần FE tự so `now`).
2. Mở WS `/ws/events?token=&farmId=` → khi có `taskChanged`/`taskDeadlineBreached`/`orderChanged` thì **re-fetch**
   `dashboard` (hoặc cập nhật cục bộ badge).
3. Click thẻ → điều hướng sang màn hình tương ứng.

**curl thật**
```bash
GW=http://localhost:8080
ACCESS=$(curl -sS -X POST "$GW/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d '{"tenantId":"farm-demo","email":"admin@example.test","password":"replace-with-a-long-random-password"}' \
  | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

curl -sS "$GW/graphql" -H "Authorization: Bearer $ACCESS" -H 'Content-Type: application/json' -d '{
  "query": "query($f:ID!,$n:Int){ dashboard(farmId:$f, recentLimit:$n){ generatedAt tasks{ total assigned accepted completed overdueAccept overdueReport } orders{ total completed failed } overdueTasks{ taskId title status acceptDeadlineAt reportDueAt } failedOrders{ orderId status failureReason totalMinor } } }",
  "variables": { "f": "farm-1", "n": 10 }
}'
```

---

### C.3 Màn hình QUẢN LÝ CÔNG VIỆC (Task) **[W]**

Vòng đời: `CREATED → ASSIGNED → ACCEPTED → COMPLETED` (hoặc `CANCELLED`). Monitor tự phát cảnh báo quá hạn.

**Tính năng**: tạo task, giao (assign, set deadline nhận/báo cáo), nhận (accept), hoàn thành (complete), hủy (cancel),
list + lọc, xem chi tiết, badge quá hạn.

**DTO**
```ts
type TaskType   = "FEEDING" | "CLEANING" | "INSPECTION" | "MEDICATION" | "OTHER";
type TaskStatus = "CREATED" | "ASSIGNED" | "ACCEPTED" | "COMPLETED" | "CANCELLED";

// POST /api/v1/livestock/tasks   scope tasks:write   header Idempotency-Key
interface CreateTaskRequest { farmId: string; title: string; type?: TaskType; assigneeId?: string; dueAtEpochMs?: number; }
interface CreateTaskResponse { taskId: string; status: string; }  // status dạng đầy đủ TASK_STATUS_*

// POST /api/v1/livestock/tasks/{id}/assign   scope tasks:write
interface AssignRequest { assigneeId: string; acceptWindowSeconds?: number; reportWindowSeconds?: number; }

// POST /{id}/accept  (no body) · /{id}/complete { note?: string } · /{id}/cancel { reason?: string }

// GET /api/v1/livestock/tasks?farmId=&status=&assigneeId=&limit=  scope farm:read
// GET /api/v1/livestock/tasks/{id}  scope farm:read
interface Task {
  taskId: string; farmId: string; title: string; type: string; status: string; assigneeId: string;
  dueAt?: number; assignedAt?: number; acceptDeadlineAt?: number; acceptedAt?: number;
  reportDueAt?: number; reportedAt?: number; completedAt?: number;   // epoch ms
}
interface TaskListResponse { count: number; tasks: Task[]; }
```

**Validate & bắt lỗi**
- Create: `farmId`, `title` **bắt buộc** + `Idempotency-Key` non-blank → thiếu = **400**. `type` sai enum → backend
  coi là `UNSPECIFIED` (không lỗi) nhưng FE nên chỉ cho chọn trong danh sách.
- Assign: `assigneeId` **bắt buộc non-blank** → thiếu = **400**. `acceptWindowSeconds`/`reportWindowSeconds` là số dương (giây).
- Accept/Complete/Cancel: transition sai trạng thái (vd accept task đã COMPLETED) → **409** (`FAILED_PRECONDITION`); task không tồn tại → **404**.
- List: `farmId` **bắt buộc** (query). `status` sai enum → không lọc (trả tất cả) — FE chỉ gửi enum hợp lệ.

**Flow request & chuyển màn hình**
1. List (`GET tasks`) → bảng trạng thái + badge quá hạn (tính từ `acceptDeadlineAt`/`reportDueAt` so với now).
2. "Tạo" → form → `POST tasks` (Idempotency-Key) → thêm dòng, ở lại màn hình.
3. Chọn task → panel chi tiết; nút hiển thị **theo trạng thái hiện tại**:
   - `CREATED` → nút **Giao** (assign).
   - `ASSIGNED` → nút **Nhận** (accept) + badge nếu quá `acceptDeadlineAt`.
   - `ACCEPTED` → nút **Hoàn thành** (complete) + badge nếu quá `reportDueAt`.
   - Mọi trạng thái chưa kết thúc → nút **Hủy**.
4. Mỗi hành động → gọi endpoint tương ứng → cập nhật dòng bằng response (Task đầy đủ) + qua WS `taskChanged`.
5. Không rời màn hình sau hành động; toast kết quả.

**curl thật** (dùng `$ACCESS`, `$GW` từ C.1)
```bash
# tạo task (cần Idempotency-Key)
TID=$(curl -sS -X POST "$GW/api/v1/livestock/tasks" -H "Authorization: Bearer $ACCESS" \
  -H "Idempotency-Key: task-$(uuidgen)" -H 'Content-Type: application/json' \
  -d '{"farmId":"farm-1","title":"Kiểm tra chuồng A","type":"INSPECTION"}' \
  | sed -n 's/.*"taskId":"\([^"]*\)".*/\1/p')
# giao (set cửa sổ nhận 120s, báo cáo 600s)
curl -sS -X POST "$GW/api/v1/livestock/tasks/$TID/assign" -H "Authorization: Bearer $ACCESS" \
  -H 'Content-Type: application/json' -d '{"assigneeId":"worker-1","acceptWindowSeconds":120,"reportWindowSeconds":600}'
# nhận -> hoàn thành
curl -sS -X POST "$GW/api/v1/livestock/tasks/$TID/accept"   -H "Authorization: Bearer $ACCESS"
curl -sS -X POST "$GW/api/v1/livestock/tasks/$TID/complete" -H "Authorization: Bearer $ACCESS" \
  -H 'Content-Type: application/json' -d '{"note":"done"}'
# list + get
curl -sS "$GW/api/v1/livestock/tasks?farmId=farm-1&status=COMPLETED&limit=20" -H "Authorization: Bearer $ACCESS"
curl -sS "$GW/api/v1/livestock/tasks/$TID" -H "Authorization: Bearer $ACCESS"
```

---

### C.4 Màn hình LỊCH ĐỊNH KỲ (Schedule) **[W]**

**Tính năng**: tạo lịch cron sinh task tự động, list lịch, xem `nextRunAt`.

**DTO**
```ts
// POST /api/v1/livestock/schedules   scope tasks:write
interface CreateScheduleRequest {
  farmId: string; title: string; type?: TaskType;
  cronExpression: string;   // cron Spring 6 trường: "giây phút giờ ngày tháng thứ"
  timeZone: string;         // IANA, vd "Asia/Ho_Chi_Minh"
  assigneeId?: string; enabled?: boolean;   // enabled mặc định true
}
// GET /api/v1/livestock/schedules?farmId=&limit=   scope farm:read
interface Schedule {
  scheduleId: string; farmId: string; title: string; type: string;
  cronExpression: string; timeZone: string; enabled: boolean;
  assigneeId?: string; nextRunAt?: number;   // epoch ms
}
interface ScheduleListResponse { count: number; schedules: Schedule[]; }
```

**Validate & bắt lỗi**
- `farmId`, `title`, `cronExpression` **bắt buộc** → thiếu = **400**.
- Cron sai cú pháp hoặc `timeZone` không hợp lệ (IANA) → backend **400** (`INVALID_ARGUMENT`). FE validate sơ bộ cron
  (6 trường) + gợi ý timezone từ danh sách IANA trước khi gửi.
- Sau tạo, `nextRunAt` được trả → hiển thị "Lần chạy kế tiếp".

**Flow**
1. List → bảng lịch + cột `nextRunAt`.
2. "Tạo lịch" → form cron + timezone (+ preview mô tả cron dạng người-đọc ở FE) → `POST schedules` → thêm dòng.
3. Task tự sinh sẽ xuất hiện ở **Quản lý công việc** (generator backend) — không cần FE làm gì; realtime qua WS `taskChanged`.

**curl thật**
```bash
# tạo lịch cron mỗi phút (6 trường Spring: giây phút giờ ngày tháng thứ)
curl -sS -X POST "$GW/api/v1/livestock/schedules" -H "Authorization: Bearer $ACCESS" \
  -H 'Content-Type: application/json' -d '{
    "farmId":"farm-1","title":"Cho ăn định kỳ","type":"FEEDING",
    "cronExpression":"0 * * * * *","timeZone":"Asia/Ho_Chi_Minh","enabled":true}'
# list
curl -sS "$GW/api/v1/livestock/schedules?farmId=farm-1&limit=20" -H "Authorization: Bearer $ACCESS"
```

---

### C.5 Màn hình ĐÀN VẬT NUÔI (Animal) **[W]**

**Tính năng**: đăng ký animal (idempotent theo tag trong farm), get, list theo farm/batch.

**DTO**
```ts
type AnimalStatus = "ANIMAL_STATUS_ACTIVE" | "ANIMAL_STATUS_SICK" | "ANIMAL_STATUS_SOLD" | "ANIMAL_STATUS_DECEASED";

// POST /api/v1/livestock/animals   scope tasks:write   (KHÔNG cần Idempotency-Key; idempotent theo tagCode)
interface RegisterAnimalRequest {
  farmId: string; tagCode: string; species?: string; barnId?: string; batchId?: string; birthDateEpochMs?: number;
}
// GET /api/v1/livestock/animals/{id}   ·   GET /api/v1/livestock/animals?farmId=&batchId=&limit=   scope farm:read
interface Animal {
  animalId: string; farmId: string; tagCode: string; status: string;
  species?: string; barnId?: string; batchId?: string; birthDate?: number;   // epoch ms
}
interface AnimalListResponse { count: number; animals: Animal[]; }
```

**Validate & bắt lỗi**
- `farmId`, `tagCode` **bắt buộc** → thiếu = **400**.
- Đăng ký lại cùng `tagCode` trong cùng farm là **idempotent** (trả animal cũ, không tạo trùng) — FE hiển thị "đã tồn tại, dùng bản hiện có". (Nếu backend coi trùng là lỗi trạng thái → **409**; xử lý cả hai.)

**Flow**
1. List theo farm/batch → bảng.
2. "Đăng ký" → form → `POST animals` → thêm/hoặc trả bản hiện có → cập nhật danh sách.

**curl thật**
```bash
# đăng ký animal (idempotent theo tagCode trong farm — KHÔNG cần Idempotency-Key)
curl -sS -X POST "$GW/api/v1/livestock/animals" -H "Authorization: Bearer $ACCESS" \
  -H 'Content-Type: application/json' -d '{"farmId":"farm-1","tagCode":"BO-001","species":"bò","batchId":"batch-1"}'
# list theo farm/batch + get
curl -sS "$GW/api/v1/livestock/animals?farmId=farm-1&batchId=batch-1&limit=20" -H "Authorization: Bearer $ACCESS"
curl -sS "$GW/api/v1/livestock/animals/<animalId>" -H "Authorization: Bearer $ACCESS"
```

---

### C.6 Màn hình KHO (Inventory setup) **[W]**

**Tính năng**: tạo item (mặt hàng), nạp kho (receipt) — dữ liệu nền để đặt hàng saga có tồn kho.

**DTO**
```ts
// POST /api/v1/inventory/items   scope inventory:write   header Idempotency-Key
interface CreateItemRequest  { sku: string; name: string; unit: string; reorderThreshold?: string; }  // reorderThreshold (thập phân, cùng đơn vị) > 0 -> item được lowStock theo dõi
interface CreateItemResponse { itemId: string; sku: string; }

// POST /api/v1/inventory/receipts   scope inventory:write   header Idempotency-Key
interface ReceiveRequest  { itemId: string; farmId: string; warehouseId: string; quantity: string; unit: string; }  // quantity dạng chuỗi thập phân
interface ReceiveResponse { movementId: string; lotId: string; }
```

**Validate & bắt lỗi**
- Tất cả field trên **bắt buộc** + `Idempotency-Key`. `quantity` là **chuỗi số thập phân dương** (vd "10", "2.5").
- Item/warehouse không hợp lệ ở nạp kho → **400/404** tuỳ backend.
- FE nên nạp kho **trước** khi cho đặt hàng cùng item/warehouse (nếu không → order sẽ FAILED do thiếu kho, có compensation).

**Flow**
1. Tạo item → `POST items` → lưu `itemId` cho form đặt hàng.
2. Nạp kho → `POST receipts` (chọn item + warehouse + số lượng).
3. Điều hướng sang **Đặt hàng** với item/warehouse vừa chuẩn bị.

**curl thật**
```bash
# tạo item (cần Idempotency-Key)
ITEM=$(curl -sS -X POST "$GW/api/v1/inventory/items" -H "Authorization: Bearer $ACCESS" \
  -H "Idempotency-Key: item-$(uuidgen)" -H 'Content-Type: application/json' \
  -d '{"sku":"FEED-01","name":"Cám hỗn hợp","unit":"kg"}' \
  | sed -n 's/.*"itemId":"\([^"]*\)".*/\1/p')
# nạp kho (quantity là chuỗi thập phân)
curl -sS -X POST "$GW/api/v1/inventory/receipts" -H "Authorization: Bearer $ACCESS" \
  -H "Idempotency-Key: rcpt-$(uuidgen)" -H 'Content-Type: application/json' \
  -d "{\"itemId\":\"$ITEM\",\"farmId\":\"farm-1\",\"warehouseId\":\"wh-A\",\"quantity\":\"100\",\"unit\":\"kg\"}"
```

---

### C.7 Màn hình ĐẶT HÀNG (Order Saga) **[W]**

Saga **orchestration đồng bộ**: giữ kho (reserve) → ghi chi phí (expense) → xác nhận (commit); lỗi bất kỳ bước →
**hoàn tác toàn bộ** (release reservation + reverse expense). Hỗ trợ **nhiều dòng hàng + warehouse-per-line**.

**Tính năng**: đặt đơn (1 hoặc nhiều dòng, mỗi dòng kho riêng), list + lọc, xem chi tiết (kèm `lines[]`), hủy (409 nếu COMPLETED), theo dõi tiến trình saga realtime.

**DTO**
```ts
type OrderStatus =
  | "ORDER_STATUS_CREATED" | "ORDER_STATUS_STOCK_RESERVED" | "ORDER_STATUS_FINANCE_POSTED"
  | "ORDER_STATUS_COMPLETED" | "ORDER_STATUS_FAILED" | "ORDER_STATUS_CANCELLED";

interface OrderLineInput {
  itemId: string; quantity: string; unit: string; currency: string;   // "VND"
  unitPriceMinor: number;                                             // minor units
  warehouseId?: string;                                               // bỏ trống -> dùng warehouseId cấp đơn
}
// POST /api/v1/orders   scope orders:write   header Idempotency-Key
interface PlaceOrderRequest {
  farmId: string;
  warehouseId: string;          // kho mặc định cấp đơn (fallback cho dòng không nêu kho)
  currency?: string;
  lines: OrderLineInput[];      // >=1 dòng (khuyến nghị luôn dùng dạng lines)
  // Tương thích cũ 1 dòng phẳng (KHÔNG bọc lines): itemId, quantity, unit, currency, unitPriceMinor
}

// GET /api/v1/orders?farmId=&status=&limit=   scope orders:read   -> { count, orders: Order[] }
// GET /api/v1/orders/{id}   scope orders:read   -> Order
// POST /api/v1/orders/{id}/cancel  { reason?: string }   scope orders:write
interface OrderLine { itemId: string; quantity: string; unit: string; unitPriceMinor: number; currency: string; warehouseId: string; }
interface Order {
  orderId: string; farmId: string; batchId: string;   // batchId = warehouse cấp đơn
  status: string; totalMinor: number; currency: string;
  createdAt?: string;           // ISO-8601
  failureReason: string;        // "" nếu không lỗi
  lines: OrderLine[];           // warehouseId rỗng = dùng batchId cấp đơn
}
```

**Validate & bắt lỗi**
- `farmId`, `warehouseId` cấp đơn **bắt buộc**; mỗi dòng cần `itemId`, `quantity`, `unit`, `currency`, `unitPriceMinor`.
- **Cạm bẫy đã biết (quan trọng)**: payload multi-line **không** gửi kèm các field 1-dòng cấp cao (itemId/quantity/…).
  `unitPriceMinor` phía backend là **boxed Long** nên vắng = null (OK); nhưng FE **luôn** gửi đúng một dạng (khuyến
  nghị **dạng `lines[]`**) để tránh nhầm. Body sai/thiếu → **400** (message có thể rỗng — FE tự hiển thị lỗi field).
- `totalMinor = Σ(quantity × unitPriceMinor)` — FE tự tính preview, đối chiếu với response.
- Đặt hàng khi thiếu kho → order về **`FAILED`** (không phải HTTP lỗi) với `failureReason` — FE hiển thị lý do, gợi ý nạp kho.
- Hủy: order `COMPLETED` → **409** (`FAILED_PRECONDITION`); order không tồn tại → **404**.

**Flow request & chuyển màn hình**
1. Form nhiều dòng (mỗi dòng chọn item + kho riêng + số lượng + đơn giá). Preview `totalMinor`.
2. Submit → `POST /orders` (Idempotency-Key) → nhận `Order` với `status`.
3. Hiển thị **tiến trình saga**: `CREATED → STOCK_RESERVED → FINANCE_POSTED → COMPLETED` (hoặc `FAILED`/`CANCELLED`).
   Cập nhật realtime qua WS `orderChanged` (mỗi bước đổi trạng thái phát 1 event).
4. Chi tiết đơn (`GET /orders/{id}`) hiển thị `lines[]` + kho từng dòng.
5. Nút **Hủy** hiển thị khi đơn **chưa** COMPLETED; ẩn/disable khi COMPLETED (bấm vẫn nhận 409 để an toàn).

**curl thật**
```bash
# đặt đơn 2 dòng, 2 kho (dùng dạng lines[])
OID=$(curl -sS -X POST "$GW/api/v1/orders" -H "Authorization: Bearer $ACCESS" \
  -H "Idempotency-Key: ord-$(uuidgen)" -H 'Content-Type: application/json' -d '{
    "farmId":"farm-1","warehouseId":"wh-A","currency":"VND",
    "lines":[
      {"itemId":"'"$ITEM"'","quantity":"5","unit":"kg","currency":"VND","unitPriceMinor":3000,"warehouseId":"wh-A"},
      {"itemId":"'"$ITEM"'","quantity":"2","unit":"kg","currency":"VND","unitPriceMinor":4000}
    ]}' | sed -n 's/.*"orderId":"\([^"]*\)".*/\1/p')
# đọc lại (kèm lines[] + tiến trình saga)
curl -sS "$GW/api/v1/orders/$OID" -H "Authorization: Bearer $ACCESS"
# list + lọc
curl -sS "$GW/api/v1/orders?farmId=farm-1&status=COMPLETED&limit=20" -H "Authorization: Bearer $ACCESS"
# hủy (order COMPLETED -> 409)
curl -sS -X POST "$GW/api/v1/orders/$OID/cancel" -H "Authorization: Bearer $ACCESS" \
  -H 'Content-Type: application/json' -d '{"reason":"khách đổi ý"}'
```

---

### C.8 Màn hình BÁO CÁO (Reporting) **[W/R]**

Xuất **bất đồng bộ**: tạo job → worker sinh file → tải về.

**Tính năng**: yêu cầu xuất (chọn loại + định dạng + khoảng thời gian), poll trạng thái, list job, tải file.

**DTO**
```ts
type ReportType   = "LIVESTOCK_TASKS" | "HEALTH" | "INVENTORY" | "BATCH_COST" | "CASH_FLOW";
type ReportFormat = "CSV" | "XLSX" | "PDF";     // mặc định CSV
type ExportStatus = "EXPORT_STATUS_QUEUED" | "EXPORT_STATUS_RUNNING" | "EXPORT_STATUS_COMPLETED" | "EXPORT_STATUS_FAILED";

// POST /api/v1/reports   scope report:write   header Idempotency-Key
interface RequestExportRequest {
  farmId: string; type: ReportType; format?: ReportFormat;
  fromEpochMs?: number; toEpochMs?: number; templateId?: string;
}
interface ExportJob {
  jobId: string; farmId: string; type: string; format: string; status: string;
  createdAt?: number; completedAt?: number; errorCode?: string;   // errorCode chỉ có khi FAILED
}
// GET /api/v1/reports/{id}   scope report:read   -> ExportJob
// GET /api/v1/reports?farmId=&limit=   scope report:read   -> { count, jobs: ExportJob[] }
// GET /api/v1/reports/{id}/download   scope report:read
interface DownloadLocation { url: string; contentType: string; expiresAt?: number; }
```

**Validate & bắt lỗi**
- `farmId` **bắt buộc** + `Idempotency-Key`. `type`/`format` sai enum → backend fallback (`UNSPECIFIED`/`CSV`) — FE chỉ gửi enum hợp lệ.
- `download` khi job **chưa** COMPLETED → **409** — FE chỉ bật nút Tải khi `status === COMPLETED`.
- Job `FAILED` → hiển thị `errorCode` (backend đã ghi root-cause vào field này).
- `LIVESTOCK_TASKS` có dữ liệu thật; các loại khác render bảng tóm tắt (v1).

**Flow**
1. Form (loại + định dạng + khoảng ngày) → `POST /reports` → nhận `jobId` (QUEUED).
2. **Poll** `GET /reports/{id}` mỗi ~2-5s tới `COMPLETED`/`FAILED` (hoặc dùng WS nếu về sau có event).
3. `COMPLETED` → bật nút Tải → `GET /reports/{id}/download` → mở `url` (dev: local path; prod: presigned URL).
4. List job (`GET /reports`) để xem lịch sử theo farm.

**curl thật**
```bash
# yêu cầu xuất PDF (cần Idempotency-Key)
JID=$(curl -sS -X POST "$GW/api/v1/reports" -H "Authorization: Bearer $ACCESS" \
  -H "Idempotency-Key: rpt-$(uuidgen)" -H 'Content-Type: application/json' \
  -d '{"farmId":"farm-1","type":"LIVESTOCK_TASKS","format":"PDF"}' \
  | sed -n 's/.*"jobId":"\([^"]*\)".*/\1/p')
# poll trạng thái tới COMPLETED/FAILED
curl -sS "$GW/api/v1/reports/$JID" -H "Authorization: Bearer $ACCESS"
# tải khi COMPLETED (chưa xong -> 409)
curl -sS "$GW/api/v1/reports/$JID/download" -H "Authorization: Bearer $ACCESS"
# list job theo farm
curl -sS "$GW/api/v1/reports?farmId=farm-1&limit=20" -H "Authorization: Bearer $ACCESS"
```

---

### C.9 Màn hình QUẢN TRỊ (Admin) **[W]** — scope `identity:admin`

**Tính năng**: liệt kê role + permission, xem role của user, gán role.

**DTO** (gateway proxy tới identity `/api/v1/admin/**`, token relay)
```ts
// GET /api/v1/admin/roles          -> { "SUPERADMIN": ["*"], "FARM_OPERATOR": [...], ... }
// GET /api/v1/admin/permissions    -> string[]  (mọi scope hệ thống biết)
// GET /api/v1/admin/users/{id}/roles       -> { roles: string[], permissions: string[] }
// PUT /api/v1/admin/users/{id}/roles/{role} -> gán role (không gán được SUPERADMIN/PLATFORM_ADMIN)
```

**Validate & bắt lỗi**
- Toàn màn hình cần `identity:admin` → thiếu = **403** (ẩn menu).
- Gán `SUPERADMIN`/`PLATFORM_ADMIN` → backend từ chối (**403/409**) — FE ẩn 2 role này khỏi lựa chọn gán.

**Flow**: liệt kê role → liệt kê scope (vốn từ) → chọn user → gán role → refresh danh sách role của user.

**curl thật** (cần `identity:admin`)
```bash
curl -sS "$GW/api/v1/admin/roles"       -H "Authorization: Bearer $ACCESS"
curl -sS "$GW/api/v1/admin/permissions" -H "Authorization: Bearer $ACCESS"
curl -sS "$GW/api/v1/admin/users/<userId>/roles" -H "Authorization: Bearer $ACCESS"
# gán role (không gán được SUPERADMIN/PLATFORM_ADMIN)
curl -sS -X PUT "$GW/api/v1/admin/users/<userId>/roles/FARM_OPERATOR" -H "Authorization: Bearer $ACCESS"
```

---

## D. Sơ đồ điều hướng tổng thể (navigation map)

```
[Đăng nhập] --login+me--> [Dashboard]
   [Dashboard] --> [Quản lý công việc] --> (assign/accept/complete/cancel, ở lại)
   [Dashboard] --> [Lịch định kỳ] --create--> (task tự sinh hiện ở Quản lý công việc)
   [Dashboard] --> [Đàn vật nuôi]
   [Dashboard] --> [Kho] --tạo item+nạp kho--> [Đặt hàng]
   [Dashboard] --> [Đặt hàng] --place--> (theo dõi saga; hủy nếu chưa COMPLETED)
   [Dashboard] --> [Báo cáo] --request--> (poll) --COMPLETED--> tải
   [Dashboard] --(nếu identity:admin)--> [Quản trị]
   (bất kỳ) --401--> [Đăng nhập]
```

## E. Checklist kỹ thuật FE (v1)

- [ ] Interceptor gắn Bearer + auto-refresh + retry-once trên 401.
- [ ] Sinh `Idempotency-Key` (UUID) mỗi submit cho các POST tạo; giữ nguyên khi retry.
- [ ] Chuẩn hoá enum status (chấp nhận cả dạng ngắn `ASSIGNED` và đầy đủ `TASK_STATUS_ASSIGNED`).
- [ ] Format tiền từ minor units theo currency; format epoch ms theo timezone người dùng.
- [ ] WebSocket client (connect/backoff/reconnect, lọc theo farmId).
- [ ] Ẩn/disable action theo `permissions[]`; luôn nhận diện 403 như trạng thái hợp lệ.
- [ ] Xử lý order `FAILED` (không phải HTTP lỗi) và job report `FAILED` (đọc `errorCode`).
