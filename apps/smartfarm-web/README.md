# SmartFarm Web v1 — Gateway only

Frontend tạm thời để **xem và thao tác các tính năng thực có** trong Gateway API v1 do bạn cung cấp (tài liệu ngày 2026-09-28). React 19 + TypeScript + Vite + TanStack Query. Không dùng mock data, không gọi thẳng service nội bộ. Đặt thư mục này cạnh `apps/` trong workspace; không thêm vào Maven reactor.

## Chạy dev

Yêu cầu Node.js >=20.19 hoặc >=22.12. Chạy Gateway :8080, Identity, các service cần dùng và MQTT nếu muốn realtime. Sau đó:

```bash
cd smartfarm-web
npm install
npm run dev
# http://127.0.0.1:5173
npm run build
```

Vite dev proxy `/api`, `/graphql`, `/ws` tới **Gateway :8080**. Không có proxy Identity riêng. Đăng nhập bằng tenant/email/password thật. Điền `farmId` trên thanh đầu trang để tải dashboard và các danh sách. Swagger của Gateway: http://localhost:8080/swagger-ui.html.

## Màn hình

- Login/logout + refresh token trong bộ nhớ; `/auth/me` lấy quyền; UI ẩn thao tác không đủ scope, nhưng BE luôn phải kiểm tra quyền.
- Dashboard GraphQL; task tạo/giao/nhận/hoàn thành/hủy + lọc; vật nuôi, lịch cron.
- Kho tạo item, nhập kho, tồn GraphQL; đơn hàng nhiều dòng và saga; báo cáo tạo job/poll/download.
- Chi phí lô, dòng tiền, low-stock GraphQL; admin tạo user và cấp/gỡ role; event WebSocket.
- Không tạo trang Health vì tài liệu không mô tả route Health public qua Gateway.

## Điểm cần kiểm tra theo contract thực tế

- Mô tả API bạn đưa gồm hai phần có vài điểm khác nhau: phần chi tiết ưu tiên `batchId` làm kho mặc định của đơn nhiều dòng; phần curl cũ dùng `warehouseId` ở payload đơn một dòng. UI dùng **dạng nhiều dòng**. `GET /orders` theo phần chi tiết là `{count,orders}`.
- `GET /livestock/tasks`, `/animals`, `/schedules`, `/reports` không có JSON response mẫu đầy đủ; UI hỗ trợ cả mảng trực tiếp và object `{tasks|animals|schedules|reports: [...]}`. Nếu Gateway trả wrapper khác, cập nhật adapter tại trang tương ứng.
- `GET /auth/me` giả định `{userId,tenantId,roles,permissions}`. Nếu Gateway dùng tên field khác, chỉnh `src/api.ts` và `src/auth.tsx`.
- GraphQL `warehouseInventory` và `cashFlow` dùng ID/Float theo ví dụ; đối chiếu `/graphql` schema nếu scalar khác.
- Báo cáo `download` trả `{url,contentType,expiresAt}`; link mở tab mới, không đưa Bearer token vào URL.
- WebSocket dùng `?token=` vì đó là contract hiện tại. URL chứa token có thể bị log; **không dùng cách này ngoài môi trường dev**. Thiết kế production: cookie HttpOnly + CSRF phù hợp hoặc vé WS ngắn hạn một lần, HTTPS/WSS và CSP.
- Access/refresh token chỉ ở memory; tải lại trang phải đăng nhập lại. Refresh mỗi 8 phút khi tab đang mở, có thể bị 401 khi tab bị suspend. Logout cố thu hồi refresh token; dù thất bại vẫn xóa state local. Chưa có giải pháp session production.
- `Idempotency-Key` tạo mỗi lần bấm submit. Nếu mạng đứt sau khi server đã ghi mà trước khi trả response, **đừng bấm lại ngay**: key mới có thể tạo bản ghi trùng. Cần cải tiến lưu key trong bộ nhớ đến khi biết kết quả.
- Các URL proxy chỉ chạy trong Vite dev; triển khai static production cần reverse proxy `/api`, `/graphql`, `/ws` về Gateway và SPA fallback. Backend service/gRPC/MQTT không được expose trực tiếp.

## Kiểm tra

`npm run build` kiểm tra TypeScript và tạo `dist/` sau khi cài dependency. Trong môi trường tạo gói không tải được dependency nên chưa có `package-lock.json`; chạy `npm install` trên máy bạn để tạo lockfile, sau đó commit và dùng `npm ci`. Chưa có test end-to-end vì không có Gateway chạy trong môi trường dựng source; kiểm thử trên API của bạn trước khi đưa dữ liệu thật vào.
