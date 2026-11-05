# Kiến trúc FarmVerse

Bộ tài liệu này mô tả kiến trúc **thực tế trong code** và kiến trúc **mục tiêu production**, cùng chênh lệch giữa hai bên.
Quy tắc: không ghi một tính năng là "đã có" nếu chưa tìm thấy nó trong code hoặc test. Mỗi khẳng định có link tới file.

## Đọc theo thứ tự

| # | Tài liệu | Trả lời câu hỏi |
|---|---|---|
| 1 | [current-architecture.md](current-architecture.md) | Hệ thống hiện chạy thế nào? Bằng chứng ở đâu? |
| 2 | [target-architecture.md](target-architecture.md) | Production cần trông thế nào? Vì sao? Lệch ở đâu? |
| 3 | [security-threat-model.md](security-threat-model.md) | Ranh giới tin cậy, tài sản, mối đe dọa và biện pháp (STRIDE) |
| 4 | [authentication-flow.md](authentication-flow.md) | Login, TOTP, session, logout, vòng đời token, BFF gọi Farm |
| 5 | [deployment.md](deployment.md) | Redis, PostgreSQL, mTLS, chứng chỉ/secret, observability |
| 6 | [adr/](adr/) | Quyết định kiến trúc đã chốt hoặc đang đề xuất |

## Sơ đồ tổng quan (hiện tại)

```mermaid
flowchart LR
    B[Trình duyệt] -- "HTTP dev / HTTPS edge prod<br/>cookie FV_SESSION" --> GW[api-gateway BFF :8080]
    B -- "trang login + TOTP<br/>cookie FV_IDP_SESSION" --> ID[identity-service :8092<br/>SAS + TOTP]
    GW -- "mTLS: token, JWKS, userinfo" --> ID
    GW -- "mTLS + Bearer (TokenRelay)<br/>/api/farm/**" --> F[farm-service :8082<br/>Resource Server]
    F -- "mTLS: JWKS (cache)" --> ID
    GW --- R[(Redis<br/>session + rate limit)]
    ID --- PI[(PostgreSQL<br/>farmverse_identity)]
    F --- PF[(PostgreSQL<br/>farmverse_farm)]
```

mTLS chỉ bật với profile `mtls` (prod luôn bật). Xem [current-architecture.md](current-architecture.md).

## Tài liệu liên quan

- Đọc mã theo luồng (cho người mới): [`docs/code-walkthrough.md`](../code-walkthrough.md).
- Thiết kế: [`content/thiet-ke-farmverse.md`](../../content/thiet-ke-farmverse.md).
- Pattern chi tiết (BFF, OIDC/SAS, hexagonal + multi-tenant, mTLS, gRPC): [`docs/code-walkthrough.md` mục 7](../code-walkthrough.md#7-các-pattern-trong-dự-án).
- Vận hành: [`docs/operations/`](../operations/).
