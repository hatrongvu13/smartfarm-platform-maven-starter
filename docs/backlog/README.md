# Backlog — kế hoạch từng phiên bản

Thư mục này lưu **các phần việc sẽ làm cho từng phiên bản** của ứng dụng (kế hoạch, yêu cầu, gợi ý kỹ thuật). Tách khỏi
tài liệu mô tả hiện trạng (`docs/` cấp trên) để dễ truy vết "cái gì thuộc version nào".

## Nội dung

| File | Version | Nội dung |
|---|---|---|
| [`FE-REQUIREMENTS-v1.md`](./FE-REQUIREMENTS-v1.md) | v1 | **Require chuẩn FE, tự chứa (không link)**: mỗi màn hình → tính năng → DTO → validate/bắt lỗi → flow request & chuyển màn hình + RBAC đầy đủ. |
| [`FE-STACK-RECOMMENDATION.md`](./FE-STACK-RECOMMENDATION.md) | v1→v2 | Gợi ý stack FE (React/TS/Vite/TanStack/Zod…) **+ mô hình trang trại 3D** (Three.js/R3F/GSAP) để định vị kho + khu vực + giao task theo vị trí. |
| [`BACKLOG-v2.md`](./BACKLOG-v2.md) | v2 | Backlog v2: nền tảng 3D (toạ độ warehouse/zone + task-location), read-model thật, GraphQL prod, hardening (TLS, multi-instance), Lombok. |

## Quy ước

- Mỗi phiên bản mới: thêm `FE-REQUIREMENTS-v<n>.md` (require FE) và/hoặc `BACKLOG-v<n>.md` (kế hoạch backend/hạ tầng).
- File **require** phải **tự chứa** (không dựa vào link) để đội FE lập kế hoạch độc lập.
- File **backlog** ghi rõ mục tiêu + phạm vi + DoD cho từng hạng mục.
