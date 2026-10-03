# backlog/v0.1 — Incremental runtime/CI fixes

> Lớp backlog delta cho lần incremental audit 2026-10-03 (HEAD `51f23a7`).
> KHÔNG thay thế backlog chính V1/V2/V3 (`backlog/V1-Core-Platform/`, `V2-*`, `V3-*`) — chỉ bổ sung các fix
> runtime/CI phát sinh từ delta config/test.

| ID | Title | Priority | Status | Depends on |
|---|---|---|---|---|
| V0.1-001 | Fix `test`-profile security config (flat → nested `jwt.*`) × 6 service | P0 | TODO | — |

Runtime-first. V0.1-001 chặn mọi test chạy profile `test` nhưng **không** chặn prod (ISSUE-01 đã vá).
Chi tiết trạng thái toàn dự án: `docs/PROJECT-STATUS.md`. Roadmap: `docs/10-implementation-roadmap.md`.
