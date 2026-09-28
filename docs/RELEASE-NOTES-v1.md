# SmartFarm Platform — Release Notes v1

> Bản phát hành **v1 (nền tảng tính năng cơ bản)** — cơ sở để dựng Frontend. Cập nhật 2026-09-28.
> Tài liệu liên quan: [SMARTFARM-VERSION.md](../SMARTFARM-VERSION.md) (ma trận hoàn thiện) ·
> [docs/GATEWAY-API-V1.md](./GATEWAY-API-V1.md) (API đầy đủ) · [docs/ARCHITECTURE.md](./ARCHITECTURE.md) ·
> [docs/SEQUENCES.md](./SEQUENCES.md) · [README.md](../README.md).

## 1. Tổng quan

- **13 module** Maven monorepo, **Java 17**, Spring Boot 4.1.1, Spring gRPC 1.1.1, package `com.htv.smartfarm`.
- Kiến trúc: Client → **Gateway BFF (ingress duy nhất `:8080`)** → gRPC nội bộ (loopback) tới các service →
  mỗi service DB riêng; sự kiện async qua **MQTT** (outbox → broker). Zero-trust per-service token + audit `actor_id`.
- Mặt đọc FE: **REST** (đầy đủ) + **GraphQL đọc** (`/graphql`); realtime **WebSocket** (`/ws/events`).
- Schema 5 DB Postgres do **Flyway** quản (`validate` + baseline).

## 2. Phạm vi tính năng v1 (đã dùng được cho FE)

| Miền | Trạng thái |
|---|---|
| Auth / Identity (login/refresh/logout/register, JWKS, RBAC scope, super-admin `*`, per-service M2M token, admin role/permission) | ✅ |
| Livestock — Task (vòng đời đầy đủ + **monitor quá hạn** nhận/báo cáo + event lifecycle qua outbox→MQTT) | ✅ |
| Livestock — Animal (đăng ký idempotent theo tag, get, list) | ✅ |
| Livestock — Schedule (cron + timezone, **generator tự sinh task**) | ✅ |
| Inventory (item/lot/balance, reserve/commit/release, FIFO) | ✅ |
| Finance (expense/income/payable/receivable/settle/reverseExpense, getTransaction/list, getBatchCost/getCashFlow) | ✅ |
| Order Saga (**orchestration** reserve→expense→commit + compensation; **multi-line + warehouse-per-line**; CQRS `ord_order_view`) | ✅ |
| Reporting (export bất đồng bộ; render thật **CSV/XLSX(POI)/PDF(OpenPDF, Unicode)**; LIVESTOCK_TASKS lấy data thật qua gRPC) | ✅ |
| GraphQL đọc (`tasks/task/orders/order`, GraphiQL `/graphiql`) | ✅ (dev wiring) |
| Realtime WebSocket (`/ws/events`, lọc tenant/farm) | ✅ |

Nền tảng vận hành (không phải màn hình nghiệp vụ): **readiness** (`GET /api/v1/platform/readiness`),
**farm-simulator** (`POST /api/v1/simulations/{type}`), **health-service** (domain health, chưa nối FE v1).

## 3. Sửa lỗi quan trọng trong chu kỳ này

- **Xung đột port gRPC 9094 (health đè finance)** → order-saga happy-path/multi-line lỗi `UNIMPLEMENTED: RecordExpense`
  gián đoạn. Nguyên nhân: `smartfarm-health-service` từng đặt gRPC **9094** + HTTP **8084** trùng finance; macOS cho
  hai bind-address (`*` vs `127.0.0.1`) cùng port sống song song → client bị chia ngẫu nhiên. **Fix**: health chuyển
  sang **gRPC 9097 / HTTP 8087**; finance độc chiếm 9094.
- **Reporting PDF `ExceptionConverter: null`** — hai lỗi nối tiếp, đều đã sửa trong `ReportRenderer.pdf()`:
  1. Font built-in Helvetica (WinAnsi) không encode được tiếng Việt → dùng **BaseFont Unicode IDENTITY_H nhúng**
     (Arial Unicode/DejaVu/Liberation, fallback Helvetica).
  2. `ClosedChannelException` do try-with-resources đóng `OutputStream` **trước** `doc.close()` trong `finally`
     (PdfWriter flush trailer vào stream đã đóng) → đổi thành **đóng `doc` cuối khối try** khi stream còn mở.
  Kèm: `ExportJobWorker` nay **log root-cause + lưu `errorCode`** thay vì nuốt lỗi thành `RENDER_FAILED` trống.
- **Schedule generator bị đói thread** khi thiếu MQTT broker (outbox relay publish đồng bộ chiếm thread scheduler
  đơn) → đặt `spring.task.scheduling.pool.size: 4` (dev) + `connectionTimeout=3s` cho MQTT (fail-fast).
- **Multi-line order 400** — DTO dùng `long` nguyên thuỷ khiến Jackson lỗi khi field vắng → đổi sang `Long`.
- **Compensation** thống nhất về `reverseExpense`; **identity** khoá loopback (single-ingress); **Flyway** cho 5 DB.

## 4. Cách chạy (dev)

MQTT broker (cho realtime + outbox + để §4 ListOrders có read-model):
```bash
mosquitto -p 1883        # hoặc: docker run -d -p 1883:1883 eclipse-mosquitto
```

7 tiến trình profile `dev` (thứ tự + port ở [GATEWAY-API-V1 §11](./GATEWAY-API-V1.md#11-khởi-động-dev--cho-người-chạy-thử)):

| Module | HTTP | gRPC |
|---|---|---|
| identity | 8092 (loopback) | — |
| livestock | 8081 | 9091 |
| inventory | 8083 | 9093 |
| finance | 8084 | 9094 |
| order | 8085 | 9095 |
| reporting | 8086 | 9096 |
| gateway (ingress) | 8080 | — |

Restart sạch (kill-by-port, tránh stale JVM + race build/restart):
```bash
bash scripts/dev/rebuild-restart.sh                 # tất cả
bash scripts/dev/rebuild-restart.sh finance order   # vài service
SKIP_BUILD=1 bash scripts/dev/rebuild-restart.sh reporting   # chỉ restart
```

> **Bẫy đã biết** (chi tiết ở [GATEWAY-API-V1 §11](./GATEWAY-API-V1.md#11-khởi-động-dev--cho-người-chạy-thử)):
> sau khi đổi `.proto` phải rebuild `libs/smartfarm-proto` + restart mọi service phụ thuộc; IntelliJ phải bật
> **Delegate IDE build/run actions to Maven** (nếu không, IntelliJ không chạy `protoc` → stub cũ → `UNIMPLEMENTED`).

## 5. Kiểm thử (regression)

```bash
bash scripts/release/v1/run-all.sh          # preflight + auth/rbac + livestock + order-saga + reporting
```
Script lẻ: `scripts/release/v1/{00-preflight,10-auth-rbac,20-livestock-flow,30-order-saga,40-reporting,50-reporting-render}.sh`.
curl độc lập: `scripts/dev/curl-06-cancel-completed.sh` (409), `scripts/dev/curl-07-multiline.sh` (multi-line COMPLETED).

Kỳ vọng sau khi có broker + finance độc chiếm 9094 + reporting nạp fix PDF: **toàn suite PASS**.

## 6. Backlog v2 (cố ý hoãn — không chặn FE)

GraphQL prod wiring + mutations · multi-instance hardening (distributed lock cho outbox/generator/export/WS) ·
reporting read-model thật cho INVENTORY/HEALTH/BATCH_COST/CASH_FLOW · OrderChanged consumer liên-service ·
reporting Flyway · hoàn thiện RPC health/simulator · **TLS/mTLS gRPC transport binding** · Lombok (đánh giá: chưa thêm).
Chi tiết ở [SMARTFARM-VERSION.md §7](../SMARTFARM-VERSION.md).
