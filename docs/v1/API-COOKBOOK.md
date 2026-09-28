# SmartFarm Platform — API Docs & curl Cookbook

Tài liệu này liệt kê mọi API **đi qua gateway** (`http://localhost:8080` — cổng công khai duy nhất) kèm curl mẫu để test từng tính năng. Các service nội bộ (identity/livestock/inventory/finance/order) chỉ nói gRPC sau gateway và **không** expose ra ngoài.

> **Swagger UI tương tác** cũng có sẵn:
> - UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
> - OpenAPI JSON: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
>
> Trong Swagger UI: gọi `POST /api/v1/auth/login`, copy `accessToken`, bấm **Authorize**, dán token → gọi mọi endpoint bảo vệ ngay trên trình duyệt.

## Quy ước

```bash
GW=http://localhost:8080          # gateway (ingress duy nhất)
TENANT=farm-demo
EMAIL=admin@example.test
PASSWORD=replace-with-a-long-random-password   # đổi qua IDENTITY_BOOTSTRAP_PASSWORD ở prod
```

Các service dev cần chạy (profile `dev`): identity :8092 (loopback), livestock :8081/gRPC 9091, inventory :8083/gRPC 9093, finance :8084/gRPC 9094, order :8085/gRPC 9095, gateway :8080.

---

## 0. Health

```bash
curl -sS $GW/actuator/health
```

---

## 1. Auth (Phase 1) — mọi thứ vào qua gateway

Identity nằm sau gateway (loopback). Auth là REST→REST proxy.

### 1.1 Đăng nhập → lấy token

```bash
curl -sS -X POST $GW/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d "{\"tenantId\":\"$TENANT\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}"
# -> { "accessToken": "...", "tokenType": "Bearer", "expiresIn": 600, "refreshToken": "..." }

# Lưu token để dùng lại:
ACCESS=$(curl -sS -X POST $GW/api/v1/auth/login -H 'Content-Type: application/json' \
  -d "{\"tenantId\":\"$TENANT\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" \
  | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
echo "$ACCESS"
```

### 1.2 Thông tin bản thân / xác thực token

```bash
curl -sS $GW/api/v1/auth/me     -H "Authorization: Bearer $ACCESS"
curl -sS $GW/api/v1/auth/verify -H "Authorization: Bearer $ACCESS"
```

### 1.3 Làm mới / đăng xuất / đổi mật khẩu

```bash
# refresh (không cần access token)
curl -sS -X POST $GW/api/v1/auth/refresh -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<REFRESH_TOKEN>"}'

# logout
curl -sS -X POST $GW/api/v1/auth/logout -H "Authorization: Bearer $ACCESS" \
  -H 'Content-Type: application/json' -d '{"refreshToken":"<REFRESH_TOKEN>"}'

# đổi mật khẩu
curl -sS -X POST $GW/api/v1/auth/password -H "Authorization: Bearer $ACCESS" \
  -H 'Content-Type: application/json' \
  -d '{"oldPassword":"<CU>","newPassword":"<MOI-min-12-ky-tu>"}'
```

### 1.4 Kiểm chứng bảo mật (nên thử)

```bash
# Không token -> 401
curl -sS -o /dev/null -w '%{http_code}\n' $GW/api/v1/auth/me
# Gọi thẳng identity từ ngoài -> không tới được (loopback). Chỉ gateway proxy được.
```

---

## 2. Phân quyền / Admin (Phase 2 + super-admin)

Admin đầu tiên của dự án là **SUPERADMIN** (quyền `*` — full mọi scope, tự động bao phủ scope thêm sau). User đăng ký sau khởi đầu **không có quyền**; admin cấp role cho họ.

Mọi endpoint `/api/v1/admin/**` yêu cầu scope `identity:admin` (super-admin có sẵn).

### 2.1 Xem vựng quyền — role & scope (MỚI)

```bash
# Mọi role kèm permission của nó
curl -sS $GW/api/v1/admin/roles -H "Authorization: Bearer $ACCESS"
# -> { "roles": { "USER":["farm:read"], "FARM_OPERATOR":[...], "SUPERADMIN":["*"], ... } }

# Mọi scope (permission) hệ thống biết — vựng từ để cấp quyền
curl -sS $GW/api/v1/admin/permissions -H "Authorization: Bearer $ACCESS"

# Role & scope hiệu lực của một user
curl -sS $GW/api/v1/admin/users/<USER_ID>/roles -H "Authorization: Bearer $ACCESS"
```

### 2.2 Tạo user mới (khởi đầu chỉ có role USER)

```bash
curl -sS -X POST $GW/api/v1/admin/users -H "Authorization: Bearer $ACCESS" \
  -H 'Content-Type: application/json' \
  -d '{"email":"worker@example.test","password":"worker-pass-1234"}'
# -> { "userId": "..." }
```

### 2.3 Cấp / gỡ role cho user

```bash
# Cấp role nghiệp vụ (vd FARM_OPERATOR: orders/inventory/tasks)
curl -sS -X PUT $GW/api/v1/admin/users/<USER_ID>/roles/FARM_OPERATOR \
  -H "Authorization: Bearer $ACCESS"

# Gỡ role
curl -sS -X DELETE $GW/api/v1/admin/users/<USER_ID>/roles/FARM_OPERATOR \
  -H "Authorization: Bearer $ACCESS"
```

> `SUPERADMIN` và `PLATFORM_ADMIN` **không** cấp được qua API (chỉ bootstrap tạo root). Không tự gỡ được ADMIN của chính mình.

### 2.4 Quản trị vựng quyền (cần `identity:platform`)

```bash
# Tạo role mới
curl -sS -X POST $GW/api/v1/admin/roles/WAREHOUSE_STAFF -H "Authorization: Bearer $ACCESS"
# Gán permission cho role
curl -sS -X PUT $GW/api/v1/admin/roles/WAREHOUSE_STAFF/permissions/inventory:write \
  -H "Authorization: Bearer $ACCESS"
```

---

## 3. Livestock — Task (dev REST qua gateway)

Yêu cầu scope: tạo task `tasks:write`, xem `farm:read`.

```bash
# Tạo task (cần Idempotency-Key)
curl -sS -X POST $GW/api/v1/livestock/tasks -H "Authorization: Bearer $ACCESS" \
  -H 'Content-Type: application/json' -H "Idempotency-Key: task-$(date +%s)" \
  -d '{"farmId":"farm-1","title":"Kiem kho dinh ky","assigneeId":"worker-1"}'
# -> { "taskId": "...", "status": "CREATED" }

# Xem task
curl -sS $GW/api/v1/livestock/tasks/<TASK_ID> -H "Authorization: Bearer $ACCESS"
```

> Truy vết: log service livestock ghi `smartfarm.audit.task task_created ... caller=svc:gateway actor_id=<user>` — mọi task truy được về người tạo.

---

## 4. Inventory — Item & Kho (dev REST qua gateway)

Yêu cầu scope `inventory:write`.

```bash
# Tạo item
curl -sS -X POST $GW/api/v1/inventory/items -H "Authorization: Bearer $ACCESS" \
  -H 'Content-Type: application/json' -H "Idempotency-Key: item-$(date +%s)" \
  -d '{"sku":"FEED-1","name":"Cam gao","unit":"kg"}'
# -> { "itemId": "...", "sku": "FEED-1" }

# Nạp kho cho item
curl -sS -X POST $GW/api/v1/inventory/receipts -H "Authorization: Bearer $ACCESS" \
  -H 'Content-Type: application/json' -H "Idempotency-Key: recv-$(date +%s)" \
  -d '{"itemId":"<ITEM_ID>","farmId":"farm-1","warehouseId":"wh-1","quantity":"100","unit":"kg"}'
# -> { "movementId": "...", "lotId": "..." }
```

---

## 5. Order — Saga (Phase 3) đặt hàng: reserve → finance → commit

Yêu cầu scope `orders:write`. Saga tự chạy: giữ kho → ghi chi phí → xác nhận; lỗi thì **compensation** (hoàn kho / đảo chi phí) và trả `ORDER_STATUS_FAILED`.

### 5.1 Happy path (đủ kho → COMPLETED)

```bash
# (đảm bảo đã tạo item + nạp kho ở mục 4, cùng warehouseId="wh-1")
curl -sS -X POST $GW/api/v1/orders -H "Authorization: Bearer $ACCESS" \
  -H 'Content-Type: application/json' -H "Idempotency-Key: order-$(date +%s)" \
  -d '{"farmId":"farm-1","warehouseId":"wh-1","itemId":"<ITEM_ID>","quantity":"10","unit":"kg","currency":"VND","unitPriceMinor":5000}'
# -> { "orderId":"...", "status":"ORDER_STATUS_COMPLETED", "totalMinor":50000, "failureReason":"" }
```

### 5.2 Ca COMPENSATION (thiếu kho → FAILED)

```bash
# Đặt cho item CHƯA nạp kho (hoặc quantity vượt tồn) -> reserve fail -> compensation
curl -sS -X POST $GW/api/v1/orders -H "Authorization: Bearer $ACCESS" \
  -H 'Content-Type: application/json' -H "Idempotency-Key: order-fail-$(date +%s)" \
  -d '{"farmId":"farm-1","warehouseId":"wh-1","itemId":"<ITEM_CHUA_CO_KHO>","quantity":"10","unit":"kg","currency":"VND","unitPriceMinor":5000}'
# -> { "status":"ORDER_STATUS_FAILED", "failureReason":"...insufficient available stock..." }
```

### 5.3 Xem order

```bash
curl -sS $GW/api/v1/orders/<ORDER_ID> -H "Authorization: Bearer $ACCESS"
```

> Truy vết saga ở log service order (`smartfarm.audit.order`): `order_step`/`order_compensate`/`order_completed`/`order_failed`, mỗi dòng có `actor_id`. State machine: `CREATED → STOCK_RESERVED → FINANCE_POSTED → COMPLETED` (nhánh lỗi → `FAILED`).

---

## Script test tự động sẵn có

- Phase 2 (per-service token + phân quyền): `scripts/phase2-curl-test.sh`
- Phase 3 (saga + compensation): `scripts/phase3-saga-curl-test.sh`

```bash
./scripts/phase2-curl-test.sh
./scripts/phase3-saga-curl-test.sh
```

## Bảng scope ↔ tính năng (tham chiếu nhanh)

| Scope | Cho phép |
|---|---|
| `farm:read` | Đọc dữ liệu farm/task |
| `tasks:write` | Tạo/giao task chăn nuôi |
| `inventory:read` / `inventory:write` | Xem / thay đổi kho |
| `orders:read` / `orders:write` | Xem / đặt hàng (saga) |
| `finance:write` | Ghi sổ tài chính (nội bộ, saga dùng) |
| `identity:admin` | Quản trị user trong tenant |
| `identity:platform` | Quản trị role/permission toàn nền |
| `*` | **SUPERADMIN** — mọi quyền, tự động gồm scope thêm sau |
