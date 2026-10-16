# Request Flows — SmartFarm Platform

> **Chuẩn hóa:** 2026-10-16  
> **Nguyên tắc:** Dùng sequence diagram trực quan, tinh giản giải thích; thể hiện chuẩn mô hình Hai Tầng Xác Thực (Two-Tier Auth) và Token Bất Đồng Bộ (Async Token) phục vụ SAGA/MQTT.

---

## 1. Xác Thực Đăng Nhập & Cấp Phát User Token (Client → Gateway → Identity)

```mermaid
sequenceDiagram
  autonumber
  participant C as Client (Web/Bruno)
  participant GW as Gateway :8080 (AuthProxy)
  participant ID as Identity :8092 (Auth)
  participant PG as Postgres (sf_accounts)
  participant REDIS as Redis (Token Family)

  C->>GW: POST /api/v1/auth/login {email, password}
  GW->>ID: REST WebClient Proxy /api/v1/auth/login
  ID->>PG: Tra cứu tài khoản, xác thực BCrypt password & Lockout
  alt Bật xác thực 2 bước (MFA)
    ID-->>GW: 202 {mfaChallengeId, mfaRequired: true}
    GW-->>C: 202 MFA required
    C->>GW: POST /api/v1/auth/mfa/verify {challengeId, code}
    GW->>ID: Proxy /api/v1/auth/mfa/verify
    ID->>ID: Xác thực mã TOTP (lockout & timing defense)
  end
  ID->>REDIS: Lưu refresh token family (chống tái sử dụng)
  ID-->>GW: 200 {accessToken (RS256, exp <= 15m), refreshToken}
  GW-->>C: Set cookies / Trả Token
```

- **Đặc tính Token Tầng 1 (User Token):** RS256, thời hạn ngắn ($\le 15$ phút), mang định danh người dùng (`sub`), vai trò (`roles`), quyền hạn (`scopes`) và tenant (`tenant_id`).

---

## 2. Mô Hình Xác Thực 2 Tầng (Two-Tier Auth) & Trao Đổi Service Token (S2S)

Để bảo vệ các microservice nghiệp vụ nội bộ khỏi nguy cơ lạm dụng token người dùng và tránh phụ thuộc vào thời hạn ngắn của User Token khi xử lý sâu trong chuỗi dịch vụ:

```mermaid
sequenceDiagram
  autonumber
  participant C as Client
  participant GW as Gateway :8080
  participant ID as Identity :9092 (gRPC)
  participant SVC as Order / Inventory Service (gRPC)

  C->>GW: HTTP Request + Bearer User JWT (exp <= 15m)
  GW->>GW: Xác thực User JWT qua SmartFarmJwtValidator
  Note over GW: User token hợp lệ & đúng thời hạn

  GW->>ID: gRPC ExchangeDelegatedToken(userToken, targetService, scopes, is_async=false)
  ID->>ID: Mint S2S Delegated Token (sub: svc:gateway, act: user, is_async: false)
  ID-->>GW: Delegated Service Token

  GW->>SVC: gRPC Call(payload) + Metadata Bearer S2S Delegated Token
  SVC->>SVC: SmartFarmJwtValidator kiểm tra chữ ký & scope
  SVC-->>GW: gRPC Response
  GW-->>C: HTTP Response
```

- **Tầng Ngoài (Client → Gateway):** Kiểm tra nghiêm ngặt `exp`, chữ ký và tenant scope.
- **Tầng Trong (Gateway → Services):** Sử dụng Delegated Token hoặc Machine Token được cấp từ Identity để bảo đảm cô lập danh tính hạ tầng.

---

## 3. Cấp Phát & Sử Dụng Token Bất Đồng Bộ (Async Token cho SAGA & Worker)

Các luồng xử lý nền (SAGA retry, Batch job, MQTT subscriber) có thể kéo dài qua nhiều giờ mà không có tương tác trực tiếp của người dùng. Để không bị đứt gãy luồng do token hết hạn (`TokenExpiredException`), Identity cấp phát Async Token riêng:

```mermaid
sequenceDiagram
  autonumber
  participant W as Order SAGA Worker / Background Job
  participant ID as Identity :9092 (gRPC)
  participant INV as Inventory :9093 (gRPC)
  participant FIN as Finance :9094 (gRPC)

  W->>ID: gRPC IssueServiceToken(clientId, tenantId, scopes, is_async=true)
  ID->>ID: Ký JWT chứa claim: "is_async": true (KHÔNG set exp, exp=null)
  ID-->>W: Async Service Token

  Note over W,INV: Chuỗi giao dịch phân tán SAGA diễn ra
  W->>INV: gRPC ReserveStock(lines) + Bearer Async Token
  INV->>INV: SmartFarmJwtValidator: is_async==true => Miễn kiểm tra exp!
  INV-->>W: Stock Reserved OK

  W->>FIN: gRPC RecordExpense(amount) + Bearer Async Token
  FIN->>FIN: SmartFarmJwtValidator: is_async==true => Miễn kiểm tra exp!
  FIN-->>W: Expense Recorded OK
```

- **Đặc tính Token Bất Đồng Bộ:**
  - Claim `is_async: true`.
  - Claim `exp`: không tồn tại (`null`).
  - `SmartFarmJwtValidator` trong `libs/smartfarm-security` tự động bỏ qua kiểm tra `missing_expiry` khi phát hiện `is_async=true`.

---

## 4. Chuỗi Giao Dịch Phân Tán (SAGA PlaceOrder: Forward & Compensation)

```mermaid
sequenceDiagram
  autonumber
  participant C as Client
  participant GW as Gateway :8080
  participant ORD as Order :9095 (FarmOrderGrpcService)
  participant WORKER as SAGA Persistent Worker
  participant INV as Inventory :9093
  participant FIN as Finance :9094
  participant MQTT as Mosquitto MQTT Broker :1883

  C->>GW: POST /api/v1/orders {Idempotency-Key, farmId, lines}
  GW->>ORD: gRPC PlaceOrder (kèm S2S Token)
  ORD->>ORD: Lưu Order (CREATED) + Khởi tạo SagaExecutionEntity
  ORD-->>GW: Trả về thông tin Order ban đầu
  GW-->>C: 200 OK (Đã tiếp nhận xử lý)

  rect rgb(240, 248, 255)
    Note over WORKER: Persistent Worker quét saga PENDING/RUNNING (1s poll loop)
    WORKER->>INV: gRPC reserveStock(Idempotency: orderId:reserve:n, Bearer Async Token)
    alt Giữ kho thành công
      WORKER->>FIN: gRPC recordExpense(Idempotency: orderId:finance, Bearer Async Token)
      alt Hạch toán tài chính thành công
        WORKER->>INV: gRPC commitReservation(Idempotency: orderId:commit:n)
        WORKER->>WORKER: Chuyển trạng thái Saga -> COMPLETED
      else Hạch toán tài chính thất bại (Lỗi số dư / hệ thống)
        Note over WORKER,FIN: Bắt đầu chuỗi bù trừ (Compensation)
        WORKER->>FIN: gRPC reverseExpense(originalKey: orderId:finance)
        WORKER->>INV: gRPC releaseReservation(reservationId)
        WORKER->>WORKER: Chuyển trạng thái Saga -> COMPENSATED
      end
    else Giữ kho thất bại (Hết hàng tồn kho)
      WORKER->>WORKER: Chuyển trạng thái Saga -> FAILED
    end
  end

  WORKER->>ORD: Lưu Outbox Event (OrderChanged protobuf)
  ORD->>MQTT: Outbox Relay publish topic smartfarm/{tenant}/{farm}/domain/order-changed/v1
  MQTT->>GW: WebSocket Bridge đẩy sự kiện realtime xuống Web Client
```

---

## 5. Luồng Cập Nhật & Thu Hồi Quyền Tức Thời (Realtime Watermark Invalidation)

```mermaid
sequenceDiagram
  autonumber
  participant Admin as Quản Trị Viên (Web/Admin)
  participant GW as Gateway
  participant ID as Identity Service
  participant PG as Postgres
  participant MQTT as Mosquitto MQTT Broker
  participant ALL as All Services (Order, Inventory, etc.)

  Admin->>GW: POST /api/v1/roles/assign hoặc Thu hồi quyền
  GW->>ID: gRPC / REST Admin Call
  ID->>PG: Cập nhật quyền trong sf_tenant_memberships & tăng watermark version
  ID->>MQTT: Publish HMAC signed event `events/identity/authorization-invalidated`
  MQTT->>ALL: Broadcast tới toàn bộ service
  ALL->>ALL: Cập nhật cache watermark nội bộ (vô hiệu hóa tức thì quyền cũ)
```

---

← [System Overview](./system-overview.md) · [Service Communications](./service-communications.md) · [Documentation Index](../index.md)
