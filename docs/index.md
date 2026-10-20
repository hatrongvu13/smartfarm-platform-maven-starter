# Danh Mục Tài Liệu Hệ Thống — SmartFarm Platform

> **Cập nhật & Chuẩn hóa:** 2026-10-16  
> **Nguyên tắc quản lý:** Tinh giản, sử dụng sơ đồ Mermaid và bảng thống kê; phân loại rõ ràng **DONE / DOING / TODO**; nguồn mã nguồn thực tế là cơ sở xác thực duy nhất.

---

## 1. Tài Liệu Cốt Lõi (Core Architecture & Standards)

| Tài Liệu | Mô Tả & Nội Dung Chính | Trạng Thái |
|---|---|:---:|
| [**System Overview**](architecture/system-overview.md) | Kiến trúc tổng thể tinh giản, Sơ đồ liên kết service, Ma trận công nghệ (Tech Stack Matrix), Thiết kế Database (Dedicated vs Dev Shared). | ✅ Chuẩn hóa |
| [**Security Architecture & Master Checklist**](security/security-architecture.md) | Kiến trúc bảo mật Identity + `smartfarm-security`, token expiry hữu hạn, **Master Checklist TODO / DOING / DONE**. | ✅ Đồng bộ |
| [**Identity / Security Common Integration**](security/identity-service-integration.md) | Hướng dẫn tích hợp resource service; tách phần JWT/gRPC common xử lý, policy/audience/tenant cần khai báo, trạng thái OAuth client và lộ trình giảm boilerplate. | ✅ Reconciled 2026-10-20 |
| [**Service Communications**](architecture/service-communications.md) | Bảng ma trận giao tiếp giữa các service (S2S), Giao thức (REST/gRPC/MQTT), Phân luồng đồng bộ & bất đồng bộ. | ✅ Chuẩn hóa |
| [**Request Flows**](architecture/request-flows.md) | Sơ đồ luồng yêu cầu chi tiết: Đăng nhập, Mô hình 2 Tầng Xác thực, token hữu hạn cho worker và Chuỗi phân tán SAGA PlaceOrder. | ✅ Chuẩn hóa |
| [**Order Saga Architecture**](architecture/order-saga.md) | Đồ thị SAGA điều phối đơn hàng (Forward & Compensation), Persistent Worker, Idempotency keys. | ✅ Chuẩn hóa |
| [**Gateway Mapping**](architecture/gateway-mapping.md) | Bảng tra cứu toàn bộ Routes REST, GraphQL và WebSocket trên API Gateway. | ✅ Chuẩn hóa |

---

## 2. Kiểm Thử, Cấu Hình & Scripts Vận Hành

| Tài Liệu | Mục Tiêu & Kịch Bản | Trạng Thái |
|---|---|:---:|
| [**Hướng Dẫn Vận Hành Toàn Bộ Scripts**](../scripts/README.md) | Cẩm nang chi tiết mục đích, cách dùng, hệ quả tác động (file, database, process, keys) và cảnh báo rủi ro cho từng script trong `scripts/`. | ✅ Đã đồng nhất & hoàn thiện |
| [**Finite Token & Saga Test Scenarios**](../bruno/ASYNC_TOKEN_AND_SAGA_TEST_SCENARIO.md) | Hướng dẫn kiểm thử thủ công qua Bruno, Ghép nối giao diện Web (`apps/smartfarm-web`), Kịch bản tự động hóa CI/CD. | ✅ Đã đồng bộ; không dùng async exp-less token |
| [**Bruno API Collection**](../bruno/README.md) | Bộ sưu tập request Bruno đầy đủ cho Identity, Gateway, Order, Inventory, Livestock, Reporting. | ✅ Hoạt động |
| [**Order Runbook & Operations**](operations/ORDER-OPERATIONS-RUNBOOK.md) | Hướng dẫn vận hành và khôi phục lỗi khi chạy Saga đơn hàng. | ✅ Chuẩn hóa |

---

## 3. Danh Mục Chi Tiết Dịch Vụ (Service Directory)

| Dịch Vụ | Module Thư Mục | Tài Liệu Chi Tiết |
|---|---|---|
| **API Gateway** | `apps/smartfarm-gateway` | [`docs/services/gateway.md`](services/gateway.md) |
| **Identity Service** | `services/smartfarm-identity-service` | [`docs/services/identity.md`](services/identity.md) |
| **Order Service** | `services/smartfarm-order-service` | [`docs/services/order.md`](services/order.md) |
| **Inventory Service** | `services/smartfarm-inventory-service` | [`docs/services/inventory.md`](services/inventory.md) |
| **Finance Service** | `services/smartfarm-finance-service` | [`docs/services/finance.md`](services/finance.md) |
| **Livestock Service** | `services/smartfarm-livestock-service` | [`docs/services/livestock.md`](services/livestock.md) |
| **Health Service** | `services/smartfarm-health-service` | [`docs/services/health.md`](services/health.md) |
| **Reporting Service** | `services/smartfarm-reporting-service` | [`docs/services/reporting.md`](services/reporting.md) |

---

## 4. Hướng Dẫn Vận Hành & Phát Triển (Operations & Development)

- [Local Development Guide](operations/local-development.md): Cài đặt môi trường, Docker Compose (PostgreSQL, Mosquitto, Redis) và chạy dev profile.
- [Configuration Reference](operations/configuration.md): Bảng tra cứu biến môi trường và file profile cấu hình (`application-dev.yml`, `application-prod.yml`).
- [MQTT ACL & Security Setup](security/mqtt-acl-mtls.md): Cấu hình chứng chỉ bảo mật và danh sách truy cập cho MQTT broker.
- [Troubleshooting & FAQ](operations/troubleshooting.md): Khắc phục sự cố thường gặp trong quá trình tích hợp.
