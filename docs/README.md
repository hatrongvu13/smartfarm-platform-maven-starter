# SmartFarm — Chỉ mục tài liệu (traceability)

Trang này **gom & phân loại toàn bộ tài liệu** trong dự án theo: **dùng cho version nào** vs **dùng chung cho toàn bộ
dự án**. Mục tiêu là dễ triển khai và truy vết. Tài liệu đã được tổ chức vào `docs/shared/` (dùng chung), `docs/v1/`
(bản v1) và `docs/backlog/` (kế hoạch từng version); mọi link đã được cập nhật theo cấu trúc này.

## 1. Tài liệu DÙNG CHUNG (mọi version)

Mô tả nền tảng, ràng buộc, chiến lược — đúng cho toàn bộ vòng đời dự án.

| Tài liệu | Nội dung |
|---|---|
| [`ARCHITECTURE.md`](./shared/ARCHITECTURE.md) | Kiến trúc, ràng buộc, Saga (orchestration), security, gRPC interceptors, TLS, dữ liệu. |
| [`SEQUENCES.md`](./shared/SEQUENCES.md) | Sequence Mermaid (task/saga, IoT simulator, readiness) + ghi chú khớp code. |
| [`REPOSITORY-STRATEGY.md`](./shared/REPOSITORY-STRATEGY.md) | Chiến lược monorepo: parent+aggregator, quản lý phiên bản tập trung, tách nhiệm vụ module. |
| [`ROADMAP.md`](./shared/ROADMAP.md) | Các giai đoạn phát triển (Phase 0→5) + Definition of Done mỗi service. |
| [`../README.md`](../README.md) | Quickstart, layout module, bảng port, dev/prod. (ở root) |
| [`../SMARTFARM-VERSION.md`](../SMARTFARM-VERSION.md) | Ma trận hoàn thiện v1 + gợi ý màn hình FE + backlog tóm tắt. (ở root) |

## 2. Tài liệu theo VERSION 1

Mô tả hiện trạng API/tính năng của bản phát hành v1.

| Tài liệu | Nội dung |
|---|---|
| [`GATEWAY-API-V1.md`](./v1/GATEWAY-API-V1.md) | Toàn bộ REST + GraphQL + WebSocket API v1 qua gateway (endpoint, scope, body). |
| [`RELEASE-NOTES-v1.md`](./v1/RELEASE-NOTES-v1.md) | Release notes v1: phạm vi, các fix, cách chạy & test. |
| [`API-COOKBOOK.md`](./v1/API-COOKBOOK.md) | curl cookbook cho mọi API v1 qua gateway + Swagger UI. |

## 3. KẾ HOẠCH & BACKLOG (theo version) — `backlog/`

Xem [`backlog/README.md`](./backlog/README.md).

| Tài liệu | Version | Nội dung |
|---|---|---|
| [`backlog/FE-REQUIREMENTS-v1.md`](./backlog/FE-REQUIREMENTS-v1.md) | v1 | Require chuẩn FE, tự chứa: màn hình → tính năng → DTO → validate → flow + RBAC. |
| [`backlog/FE-STACK-RECOMMENDATION.md`](./backlog/FE-STACK-RECOMMENDATION.md) | v1→v2 | Gợi ý stack FE + mô hình trang trại 3D (Three.js/R3F/GSAP). |
| [`backlog/BACKLOG-v2.md`](./backlog/BACKLOG-v2.md) | v2 | Backlog v2: nền tảng 3D, read-model thật, GraphQL prod, hardening, Lombok. |

## 4. Tài liệu PHÂN TÍCH / LỊCH SỬ

| Tài liệu | Nội dung |
|---|---|
| [`IMPROVEMENT-PLAN.md`](./shared/IMPROVEMENT-PLAN.md) | Đánh giá & kế hoạch cải thiện (phân tích graphify tại thời điểm 2026-09-26 — tham chiếu lịch sử). |

## 5. Quy ước đặt tài liệu về sau

Cấu trúc thư mục thực tế:
```
docs/
  README.md            (chỉ mục này)
  shared/              tài liệu dùng chung mọi version
  v1/                  tài liệu hiện trạng bản v1
  backlog/             kế hoạch từng version (require FE, backlog)
```

- **Dùng chung** → `docs/shared/` (ARCHITECTURE/SEQUENCES/REPOSITORY-STRATEGY/ROADMAP/IMPROVEMENT-PLAN).
- **Theo version (hiện trạng)** → `docs/v<n>/`, tên có hậu tố version (vd `docs/v2/GATEWAY-API-V2.md`, `docs/v2/RELEASE-NOTES-v2.md`).
- **Kế hoạch/backlog theo version** → `docs/backlog/` (require FE + backlog backend).
- Cập nhật bảng này khi thêm tài liệu mới để giữ truy vết.
