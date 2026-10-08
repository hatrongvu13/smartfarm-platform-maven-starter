# SmartFarm Web (FE)

Frontend cho SmartFarm Platform — dựng từ bộ tài liệu backend và collection Bruno.
Stack: **Vite + React 18 + TypeScript + Tailwind v4 + React Router + TanStack Query**.

## Chạy

```bash
npm install
cp .env.example .env      # tuỳ chọn
npm run dev               # http://localhost:5173
```

Backend phải chạy gateway ở `:8080` (+ identity `:8092`). Vite proxy `/api`, `/graphql`, `/actuator`, `/ws` sang `VITE_GATEWAY_URL`, nên không cần CORS khi dev.

## Cấu trúc

```
src/lib/api.ts      fetch client: Bearer, Idempotency-Key, tự refresh khi 401, gql()
src/lib/auth.tsx    AuthProvider, scopes (scope "*" = super-admin)
src/lib/farm.tsx    farmId đang chọn (ô Farm trên header)
src/pages/          Login (MFA/tenant), Bootstrap, Dashboard, Orders*, Inventory, Livestock, Reports
src/components/     ui.tsx (Button/Card/Field/Badge...), Layout.tsx
```

## Luồng auth (theo docs)

1. `GET /api/v1/platform/deployment-state` → nếu `bootstrapRequired` thì gợi ý `/bootstrap`.
2. `POST /api/v1/auth/login {email,password,tenantId?}` → `COMPLETED | MFA_REQUIRED | MFA_ENROLLMENT_REQUIRED | TENANT_SELECTION_REQUIRED`; màn Login xử lý cả 4 (kèm QR đăng ký TOTP).
3. Access token (15 phút) giữ trong memory; refresh token trong `localStorage`; 401 → tự `POST /auth/refresh` (rotating).
4. Sau khi có token: **scopes giải mã trực tiếp từ JWT** (claim `scope` space-delimited và/hoặc `scopes`; super-admin mang `*`), còn **hồ sơ/membership lấy qua GraphQL `me`** (`subjectId`, `profile{...}`, `membership{roles,farmIds,...}`). Principal **không** có trường `scopes`; nếu `me` lỗi tạm thời thì vẫn `authenticated` bằng claim JWT, không đăng xuất.

## Endpoint đã nối

| Trang | Endpoint | Ghi chú |
|---|---|---|
| Đơn hàng | `/api/v1/orders` list/get, `/drafts` create, `/drafts/{id}` edit (PUT) + xoá (DELETE `?expectedVersion`), `/drafts/{id}/submit`, `/{id}/cancel` | Idempotency-Key cho create/draft; submit + delete cần `expectedVersion` |
| Kho | `/api/v1/inventory/items`, `/receipts` (create) | **dev-only**; **không có list REST** — đọc tồn kho qua GraphQL `warehouseInventory`/`lowStock` |
| Chăn nuôi | `/api/v1/livestock/animals`, `/tasks` | tasks **dev-only** |
| Báo cáo | `/api/v1/reports` (+`/{id}`, `/{id}/download`) | **dev-only**, polling job; scope `report:read`/`report:write` (số ít) |
| Tổng quan | GraphQL `me` (profile+membership), `platformStatus` | scopes lấy từ **JWT**; REST `/api/v1/me` chỉ THIN (`{subject,tenantId}`), cần `farm:read` |

## Việc nên làm tiếp (tài liệu chưa đủ để tôi đoán)

- **Schema GraphQL** (`schema.graphqls`): đã dùng `me` (profile/membership) + `platformStatus`. Còn nên chuyển Dashboard/Kho sang `dashboard`, `warehouseInventory`, `lowStock`, `batchCost`, `cashFlow` (Kho hiện không có list REST nên bắt buộc đi GraphQL).
- **Hình dạng response** của order/animal/report chỉ suy ra từ test Bruno nên bảng đang hiển thị cột động / JSON thô; chỉnh khi có type chính xác.
- Task lifecycle (`/tasks/{id}/assign|accept|complete`), health (`/api/v1/health/*`), saga admin (`/order-sagas/{id}`) chưa có UI — body chưa được mô tả.
- WebSocket `/ws` (domain events, protobuf qua MQTT bridge) chưa nối; gateway mặc định tắt (`SMARTFARM_MQTT_EVENTS_ENABLED=false`).
- Màu thương hiệu: sửa `--color-brand-*` trong `src/index.css`.
