# Tổng Quan Kiến Trúc Hệ Thống — SmartFarm Platform

> **Chuẩn hóa:** 2026-10-16  
> **Nguyên tắc:** Sử dụng biểu đồ trực quan (Mermaid) và bảng thống kê; thông tin cô đọng, dễ tích hợp và mở rộng; nguồn mã nguồn làm chuẩn duy nhất.

---

## 1. Cấu Trúc Liên Kết Tinh Giản Của Hệ Thống (Service Interconnection)

```mermaid
flowchart TD
    subgraph Clients["Tầng Trình Diễn (Clients & Edge)"]
        Web["Web App (apps/smartfarm-web)\nReact 19 + Vite + Tailwind"]
        Bruno["Bruno API Collections\n(Manual & Automated Tests)"]
    end

    subgraph EdgeIngress["Tầng Cổng (API Gateway)"]
        GW["smartfarm-gateway (:8080)\n- Spring WebFlux (Reactive)\n- REST Proxy + GraphQL (:8080/graphql)\n- WebSocket Event Bridge (:8080/ws)"]
    end

    subgraph SecurityHub["Trung Tâm Định Danh (Security & Identity)"]
        Identity["smartfarm-identity-service (:8092 / :9092)\n- Cấp Token Đồng Bộ & Bất Đồng Bộ (is_async)\n- Xác thực RBAC, MFA, Session Management\n- JWKS Endpoint (/.well-known/jwks.json)"]
    end

    subgraph BusinessServices["Các Dịch Vụ Nghiệp Vụ Nội Bộ (Internal gRPC Services)"]
        Order["smartfarm-order-service (:8085 / :9095)\n- Order Lifecycle & SAGA Coordinator"]
        Inv["smartfarm-inventory-service (:8083 / :9093)\n- Stock Reservations & Warehouses"]
        Fin["smartfarm-finance-service (:8084 / :9094)\n- Ledger, Expenses & Reversals"]
        Livestock["smartfarm-livestock-service (:8081 / :9091)\n- Animals & Task Schedules"]
        Health["smartfarm-health-service (:8087 / :9097)\n- Observations & Alerts"]
        Reporting["smartfarm-reporting-service (:8086 / :9096)\n- Export Jobs & Aggregates"]
    end

    subgraph EventMesh["Hạ Tầng Bất Đồng Bộ (Event & Message Bus)"]
        MQTT["Mosquitto MQTT Broker (:1883)\n- Invalidation: events/identity/authorization-invalidated\n- SAGA & Domain Events: smartfarm/*/events"]
    end

    %% Client calls
    Web -->|HTTP / WS| GW
    Bruno -->|HTTP| GW

    %% Gateway dispatch
    GW -->|"REST (Auth Proxy) / gRPC (Admin)"| Identity
    GW -->|"gRPC + Delegated Service Token"| Order
    GW -->|"gRPC + Delegated Service Token"| Inv
    GW -->|"gRPC + Delegated Service Token"| Fin
    GW -->|"gRPC + Delegated Service Token"| Livestock
    GW -->|"gRPC + Delegated Service Token"| Health
    GW -->|"gRPC + Delegated Service Token"| Reporting

    %% Inter-service calls (SAGA)
    Order ==>|"gRPC (SAGA Forward/Compensate)"| Inv
    Order ==>|"gRPC (SAGA Forward/Compensate)"| Fin

    %% Event Streams
    Identity -.->|"Outbox: Auth Invalidation"| MQTT
    Order -.->|"Outbox: Order Events"| MQTT
    Livestock -.->|"Outbox: Task Events"| MQTT
    Health -.->|"Outbox: Health Alerts"| MQTT
    MQTT -.->|"Event Bridge Push"| GW
```

---

## 2. Bảng Ma Trận Công Nghệ (Tech Stack Matrix)

Bảng phân bổ công nghệ cốt lõi trên từng service và thư viện trong toàn bộ dự án:

| Module / Service | Spring Boot 4 | Spring gRPC | Spring WebFlux | Spring Data JPA | PostgreSQL (Shared Dev / Dedicated Prod) | Flyway Migration | Mosquitto MQTT | React 19 / Vite | Protobuf v3 | Spring Security / JWT |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| `smartfarm-web` | — | — | — | — | — | — | — | ✅ | — | ✅ (Token Client) |
| `smartfarm-gateway` | ✅ | ✅ (Client) | ✅ | — | — | — | ✅ (Sub/WS) | — | ✅ | ✅ (Resource Server) |
| `smartfarm-identity-service` | ✅ | ✅ (Server) | — | ✅ | ✅ | ✅ | ✅ (Outbox/Inbox)| — | ✅ | ✅ (Issuer/JWKS) |
| `smartfarm-order-service` | ✅ | ✅ (Server/Client) | — | ✅ | ✅ | ✅ | ✅ (Outbox/CQRS) | — | ✅ | ✅ (Resource Server) |
| `smartfarm-inventory-service` | ✅ | ✅ (Server) | — | ✅ | ✅ | ✅ | ✅ (Sub) | — | ✅ | ✅ (Resource Server) |
| `smartfarm-finance-service` | ✅ | ✅ (Server) | — | ✅ | ✅ | ✅ | — | — | ✅ | ✅ (Resource Server) |
| `smartfarm-livestock-service` | ✅ | ✅ (Server) | — | ✅ | ✅ | ✅ | ✅ (Outbox) | — | ✅ | ✅ (Resource Server) |
| `smartfarm-health-service` | ✅ | ✅ (Server) | — | ✅ | ✅ | ✅ | ✅ (Outbox) | — | ✅ | ✅ (Resource Server) |
| `smartfarm-reporting-service` | ✅ | ✅ (Server) | — | ✅ | ✅ | ✅ | — | — | ✅ | ✅ (Resource Server) |
| `libs/smartfarm-security` | — | ✅ (Interceptors) | ✅ (WebFlux Guard) | — | — | — | ✅ (Envelope Verifier) | — | — | ✅ (Common Core) |
| `libs/smartfarm-proto` | — | ✅ (Contracts) | — | — | — | — | — | — | ✅ | — |
| `libs/smartfarm-common-kernel` | — | — | — | — | — | — | — | — | — | ✅ (Contract Claims) |

---

## 3. Kiến Trúc Cơ Sở Dữ Liệu: Tách Biệt Nghiệp Vụ vs Dev Dùng Chung

```mermaid
flowchart TD
    subgraph TargetArchitecture["Kiến Trúc Chuẩn (Target / Production) — Database-Per-Service"]
        direction TB
        DB_ID[("PostgreSQL: identity_db\n(Schemas & Tables riêng)")]
        DB_ORD[("PostgreSQL: order_db\n(Schemas & Tables riêng)")]
        DB_INV[("PostgreSQL: inventory_db\n(Schemas & Tables riêng)")]
        DB_FIN[("PostgreSQL: finance_db\n(Schemas & Tables riêng)")]
        DB_OTH[("PostgreSQL: livestock_db / health_db")]

        S_ID["Identity Service"] --> DB_ID
        S_ORD["Order Service"] --> DB_ORD
        S_INV["Inventory Service"] --> DB_INV
        S_FIN["Finance Service"] --> DB_FIN
        S_OTH["Livestock / Health"] --> DB_OTH
    end

    subgraph DevEnvironment["Hiện Trạng Môi Trường Dev (Local Development) — Shared Instance"]
        direction TB
        SharedPG[("PostgreSQL 17 Instance Dùng Chung\n(Port :5432 - Database: 'smartfarm')\n- sf_* : Bảng Identity\n- order_* : Bảng Orders & Saga\n- inv_* : Bảng Inventory\n- fin_* : Bảng Finance\n- live_* : Bảng Livestock")]

        D_ALL["Mọi Service chạy Local (Identity, Order, Inventory, Finance...)"] -->|"${DB_URL:jdbc:postgresql://localhost:5432/smartfarm}"| SharedPG
    end
```

### So Sánh Cơ Chế Lưu Trữ

| Thuộc Tính | Môi Trường Phát Triển (Dev) | Mục Tiêu Triển Khai (Production / Target) |
|---|---|---|
| **Mô hình kết nối** | Dùng chung 1 PostgreSQL database instance (`smartfarm:5432`) | Database-per-service (Mỗi service sở hữu kết nối và DB riêng biệt) |
| **Phân vùng dữ liệu** | Phân tách theo tiền tố bảng (`sf_*`, `order_*`, `inv_*`, `fin_*`...) | Tách biệt hoàn toàn ở cấp Database/Schema vật lý |
| **Ràng buộc khóa ngoại (FK)** | Tuyệt đối **không tạo FK liên service** (giữ ranh giới microservice) | Hoàn toàn cô lập; không phụ thuộc lẫn nhau |
| **Khởi tạo Schema** | Hibernate `ddl-auto: update` (Dev) hoặc H2 in-memory (Test) | Flyway Migration bắt buộc (`ddl-auto: validate`) |
| **Lợi ích vận hành** | Nhẹ tài nguyên máy phát triển, khởi động nhanh qua 1 lệnh Docker | Tối đa hóa khả năng mở rộng, cô lập sự cố, bảo mật dữ liệu độc lập |

---

## 4. Bảng Danh Mục Service, Cổng Mạng & Giao Thức (Ports & Network)

| Tên Dịch Vụ | Module Thư Mục | HTTP Port | gRPC Port | Giao Thức & Vai Trò Chính |
|---|---|:---:|:---:|---|
| **Gateway** | `apps/smartfarm-gateway` | `8080` | — | Ingress duy nhất: REST Proxy, GraphQL (`/graphql`), WebSocket Bridge (`/ws`) |
| **Web Frontend** | `apps/smartfarm-web` | `5173` | — | Giao diện người dùng SPA (Vite Dev Server / Nginx build) |
| **Identity Service** | `services/smartfarm-identity-service` | `8092` | `9092` | Cấp phát Token (Sync/Async), Quản trị RBAC, Session gRPC, Auth HTTP |
| **Order Service** | `services/smartfarm-order-service` | `8085` | `9095` | Quản lý Đơn hàng, Điều phối SAGA Transaction qua gRPC & Outbox Relay |
| **Inventory Service** | `services/smartfarm-inventory-service` | `8083` | `9093` | Quản lý Kho, Giữ tồn kho (`reserveStock`), Xuất kho (`commitReservation`) |
| **Finance Service** | `services/smartfarm-finance-service` | `8084` | `9094` | Ghi nhận chi phí (`recordExpense`), Hoàn tác chi phí (`reverseExpense`) |
| **Livestock Service** | `services/smartfarm-livestock-service` | `8081` | `9091` | Quản lý Đàn vật nuôi, Lịch tiêm, Nhiệm vụ chăm sóc |
| **Health Service** | `services/smartfarm-health-service` | `8087` | `9097` | Hồ sơ sức khỏe, Theo dõi triệu chứng, Cảnh báo dịch bệnh |
| **Reporting Service** | `services/smartfarm-reporting-service` | `8086` | `9096` | Tổng hợp báo cáo, Xử lý export file bất đồng bộ |
| **MQTT Broker** | Hạ tầng (`compose.yaml`) | — | `1883` | Trao đổi sự kiện domain bất đồng bộ & Watermark Invalidation |

---

## 5. Quy Chuẩn Tích Hợp & Mở Rộng Tính Năng

1. **Thêm API Mới:**
   - Định nghĩa hợp đồng tại `libs/smartfarm-proto` (`.proto`).
   - Biên dịch sinh mã: `mvn compile -pl libs/smartfarm-proto`.
   - Cài đặt Server Impl trong Service nghiệp vụ; Gateway chỉ định tuyến qua gRPC stub tương ứng.
2. **Bảo Mật API & Service Token:**
   - Mọi Service nghiệp vụ phải include `libs/smartfarm-security`.
   - Cuộc gọi gRPC từ Gateway hoặc Service-to-Service bắt buộc gắn `BearerCallCredentials` chứa Service Token được cấp từ Identity.
   - Luồng bất đồng bộ (Background job, SAGA) dùng token với `is_async = true`.
3. **Mở Rộng Sự Kiện (Events):**
   - Sự kiện xuất bản dưới dạng Protobuf `DomainEvent`.
   - Sử dụng Transactional Outbox Pattern để cam kết dữ liệu DB và xuất bản MQTT đồng nhất.
