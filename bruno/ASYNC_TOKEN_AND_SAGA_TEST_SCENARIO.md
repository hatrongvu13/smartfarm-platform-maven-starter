# Kịch Bản Kiểm Thử Token Hữu Hạn & Luồng Saga

> **Tài liệu kiểm thử thủ công và kịch bản tự động hóa (Automated Testing Reference)**  
> **Áp dụng cho:** API Gateway, Identity Service, Order Service, Inventory Service, Finance Service.
> **Lưu ý:** Tên file được giữ để không làm hỏng liên kết hiện có; Identity không còn phát hành token `is_async` hoặc JWT thiếu `exp`.

---

## 1. Mục Tiêu Kiểm Thử

1. **Xác thực 2 tầng (Two-Tier Authentication):**
   - **Tầng Client -> Gateway:** Client sử dụng User Access Token (JWT có thời hạn ngắn $\le 15$ phút, yêu cầu bắt buộc `exp`, `iat`, chữ ký RS256).
   - **Tầng Gateway -> Service (Service-to-Service - S2S):** Gateway chuyển tiếp request đính kèm **Service Token riêng** (được cấp phát lại từ `Identity Service` qua `ExchangeDelegatedToken`, mang danh tính machine `client_id=gateway` và người dùng ủy quyền `act`).
2. **Cấp phát Token Hữu Hạn (Sync và Worker):**
   - Mọi JWT phải có `exp` hữu hạn, bất kể request đồng bộ hay job chạy nền.
   - Worker/SAGA phải lấy token ngắn hạn cho mỗi execution/retry; không giữ bearer token vô hạn qua thời gian queue.
   - Worker callers hiện đang dùng custom gRPC issuance; migration sang OAuth `client_credentials` là backlog.
3. **Thống nhất Bảo mật (Unified Security Common):**
   - Tất cả service sử dụng chung cấu hình và verifier từ `libs/smartfarm-security`.
   - Token worker được bảo vệ bởi: chữ ký RSA của Identity, đối chiếu client allowlist, expiry hữu hạn, và cơ chế thu hồi qua **Authorization Version Watermark Store** (MQTT topic `events/identity/authorization-invalidated`).

---

## 2. Kịch Bản Kiểm Thử Thủ Công (Manual Test Scenarios qua Bruno)

### Kịch bản 1: Xác thực User và Đăng nhập (Client -> Gateway)
- **Thao tác:** Chạy request `bruno/01 - identity/01 - login.bru` (hoặc sau khi enrollment: `04 - login (after enrollment).bru`).
- **Kỳ vọng:**
  - HTTP 200 OK.
  - Nhận về `accessToken` dạng JWT.
  - Giải mã JWT thấy: `sub`, `tenant_id`, `exp` (15 phút sau `iat`), `roles`, `scope`, `token_type = "user"`.
  - Token này **bắt buộc có `exp`**. Nếu cố tình xóa `exp`, Gateway/Resource server trả về lỗi `missing_expiry`.

### Kịch bản 2: Gateway Ủy Quyền Service Token (S2S Delegated Token)
- **Thao tác:** Chạy request `bruno/04 - order/01 - create draft order.bru` với Bearer Token từ Kịch bản 1.
- **Kỳ vọng:**
  - Gateway nhận request, xác minh user token hợp lệ.
  - Gateway gọi nội bộ đến `IdentityTokenService.ExchangeDelegatedToken` qua gRPC.
  - Identity xác minh:
    - Caller là Gateway hợp lệ (`client_id = gateway`).
    - Actor ID và Tenant ID trùng khớp với User JWT.
    - Cấp phát Delegated Service Token (`sub = "svc:gateway"`, `act.sub = user_id`, `token_type = "service"`).
  - Gateway chuyển tiếp cuộc gọi sang Order Service thành công. Nhận HTTP 200/201 kèm `orderId`.

### Kịch bản 3: Luồng SAGA với Token Hữu Hạn
- **Thao tác:** Chạy request `bruno/04 - order/11 - async-saga-execution-flow.bru` (hoặc `02 - submit draft order.bru`).
- **Kỳ vọng:**
  - Đơn hàng chuyển trạng thái từ `DRAFT` sang xử lý SAGA.
  - Worker SAGA chạy nền sử dụng Service Token có `exp` hữu hạn:
    - Bước 1: `reserveStock` gọi sang `Inventory Service`.
    - Bước 2: `recordExpense` gọi sang `Finance Service`.
    - Bước 3: `commitReservation` hoàn tất tồn kho.
  - Nếu queue/retry kéo dài qua TTL token, worker lấy token mới trước lần gọi tiếp theo; không tái sử dụng token hết hạn.
  - `SmartFarmJwtValidator` yêu cầu chữ ký hợp lệ và `exp` hữu hạn; token thiếu expiry bị từ chối.

### Kịch bản 4: Thu Hồi Token Bất Đồng Bộ Tức Thì (Watermark Invalidation)
- **Thao tác:**
  - Admin thu hồi quyền hoặc khóa phiên người dùng / tenant.
  - Identity Service đẩy sự kiện `AuthorizationInvalidated` qua MQTT topic `events/identity/authorization-invalidated`.
- **Kỳ vọng:**
  - `AuthorizationVersionWatermarkStore` tại Gateway và Order Service nâng mức watermark của tenant/actor.
  - Các request tiếp theo mang version cũ hơn watermark bị chặn với mã lỗi `PERMISSION_DENIED` / `UNAUTHENTICATED`.

---

## 3. Ghép Nối Với Giao Diện Web (`apps/smartfarm-web`)

1. Khởi chạy ứng dụng Web tại `apps/smartfarm-web` (`npm run dev`).
2. Mở trình duyệt tại trang `http://localhost:5173`:
   - Truy cập trang **Đăng nhập (`/login`)**: Nhập thông tin tài khoản admin/user.
   - Truy cập trang **Tạo đơn hàng (`/orders/new`)**: Nhập thông tin vật tư, số lượng, chọn kho và bấm **"Tạo draft"**.
   - Bấm **"Submit"** đơn hàng: Giao diện gửi request submit tới Gateway, kích hoạt chuỗi xử lý S2S và Async Saga Worker.
   - Quan sát trạng thái đơn hàng cập nhật tự động từ `DRAFT` $\to$ `SUBMITTED` $\to$ `COMPLETED`.

---

## 4. Tài Liệu Cho Việc Tự Động Hóa Kiểm Thử (Automated CI/CD Test Plan)

Để tích hợp vào pipeline kiểm thử tự động không cần môi trường nền phức tạp:
1. **Kiểm thử cấp Unit & Library:**
   - Chạy lệnh: `mvn test -pl libs/smartfarm-common-kernel,libs/smartfarm-security`
   - Xác nhận validator từ chối JWT thiếu `exp` và chấp nhận các JWT có expiry hợp lệ; không còn nhánh bypass dành cho token async.
2. **Kiểm thử cấp Service (Identity & gRPC Token Service):**
   - Chạy lệnh: `mvn test -pl services/smartfarm-identity-service -am`
   - Xác nhận Identity service tests pass, bao gồm protocol test cho OIDC discovery/JWKS và token `client_credentials` có audience/expiry hữu hạn. Protocol E2E PKCE và PostgreSQL release gate vẫn là release gates.
3. **Kiểm thử CLI Bruno qua Runner:**
   - Sử dụng Bruno CLI (`bru run bruno --env local`) trong pipeline staging khi các container hạ tầng được khởi động.
