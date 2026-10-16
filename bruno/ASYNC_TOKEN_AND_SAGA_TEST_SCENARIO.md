# Kịch Bản Kiểm Thử Token Bất Đồng Bộ & Luồng Saga (Async Token & Saga Test Scenarios)

> **Tài liệu kiểm thử thủ công và kịch bản tự động hóa (Automated Testing Reference)**  
> **Áp dụng cho:** API Gateway, Identity Service, Order Service, Inventory Service, Finance Service.

---

## 1. Mục Tiêu Kiểm Thử

1. **Xác thực 2 tầng (Two-Tier Authentication):**
   - **Tầng Client -> Gateway:** Client sử dụng User Access Token (JWT có thời hạn ngắn $\le 15$ phút, yêu cầu bắt buộc `exp`, `iat`, chữ ký RS256).
   - **Tầng Gateway -> Service (Service-to-Service - S2S):** Gateway chuyển tiếp request đính kèm **Service Token riêng** (được cấp phát lại từ `Identity Service` qua `ExchangeDelegatedToken`, mang danh tính machine `client_id=gateway` và người dùng ủy quyền `act`).
2. **Cấp phát & Phân biệt Token Theo Luồng (Sync vs Async):**
   - **Luồng Đồng bộ (Sync - REST / gRPC tức thời):** Token có thời hạn (`exp` ngắn, TTL giới hạn 1–15 phút).
   - **Luồng Bất đồng bộ (Async - SAGA Worker qua MQTT / Background Jobs):** Token mang cờ `is_async = true`, không bị ép buộc thời hạn hết hạn (`exp = null`), giúp tiến trình SAGA chạy nền, bù trừ dữ liệu (compensation) và retry không bị đứt gãy do timeout token.
3. **Thống nhất Bảo mật (Unified Security Common):**
   - Tất cả service sử dụng chung cấu hình và verifier từ `libs/smartfarm-security`.
   - Token async được bảo vệ bởi: chữ ký RSA của Identity, đối chiếu client allowlist, và cơ chế thu hồi tức thì qua **Authorization Version Watermark Store** (MQTT topic `events/identity/authorization-invalidated`).

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

### Kịch bản 3: Luồng Bất Đồng Bộ SAGA & Token Không Thời Hạn (Async Token)
- **Thao tác:** Chạy request `bruno/04 - order/11 - async-saga-execution-flow.bru` (hoặc `02 - submit draft order.bru`).
- **Kỳ vọng:**
  - Đơn hàng chuyển trạng thái từ `DRAFT` sang xử lý SAGA.
  - Worker SAGA chạy nền sử dụng Service Token với `is_async = true`:
    - Bước 1: `reserveStock` gọi sang `Inventory Service`.
    - Bước 2: `recordExpense` gọi sang `Finance Service`.
    - Bước 3: `commitReservation` hoàn tất tồn kho.
  - Nếu một bước gặp sự cố mạng hoặc hàng đợi kéo dài qua 15 phút, worker retry mà **không bị lỗi token expired**.
  - Service nhận token kiểm tra qua `SmartFarmJwtValidator`: nhận biết `is_async = true` nên bỏ qua điều kiện `missing_expiry`, đồng thời xác thực tính toàn vẹn chữ ký và tenant version.

### Kịch bản 4: Thu Hồi Token Bất Đồng Bộ Tức Thì (Watermark Invalidation)
- **Thao tác:**
  - Admin thu hồi quyền hoặc khóa phiên người dùng / tenant.
  - Identity Service đẩy sự kiện `AuthorizationInvalidated` qua MQTT topic `events/identity/authorization-invalidated`.
- **Kỳ vọng:**
  - `AuthorizationVersionWatermarkStore` tại Gateway và Order Service nâng mức watermark của tenant/actor.
  - Các request tiếp theo (kể cả async token) mang version cũ hơn watermark bị chặn ngay lập tức với mã lỗi `PERMISSION_DENIED` / `UNAUTHENTICATED`.

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
   - Đảm bảo 60/60 test passes, bao gồm:
     - `acceptsAsyncTokenWithoutExpiry`: Xác thực token async không có `exp` thành công.
     - `rejectsSyncTokenWithoutExpiry`: Từ chối token sync thiếu `exp` (`missing_expiry`).
2. **Kiểm thử cấp Service (Identity & gRPC Token Service):**
   - Chạy lệnh: `mvn test -pl services/smartfarm-identity-service -am`
   - Đảm bảo 95/95 test passes, bao gồm:
     - `issuesAsyncMachineToken`: Kiểm tra Identity cấp token async qua gRPC với `expiresInSeconds = 0`.
     - `delegatedExchangeRequiresActorAndTenantToMatchVerifiedUserJwt`: Kiểm tra phòng chống giả mạo token ủy quyền.
3. **Kiểm thử CLI Bruno qua Runner:**
   - Sử dụng Bruno CLI (`bru run bruno --env local`) trong pipeline staging khi các container hạ tầng được khởi động.
