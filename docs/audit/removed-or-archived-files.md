# Removed / Archived Files — SmartFarm Platform

> Đợt audit 2026-10-06 @ HEAD `eaaa112`.

## Đã XÓA
**Không có.** Nguyên tắc an toàn: không xóa chỉ vì cũ/ít tham chiếu. Mọi file hoặc còn dùng, hoặc là archive-candidate giữ lịch sử.

## Đã ARCHIVE
**Không có trong đợt này** — các ứng viên archive (13 CHUNK summary) được GIỮ NGUYÊN vị trí vì vẫn mang giá trị lịch sử quá trình cleanup; chỉ ghi nhận là archive-candidate, chưa di chuyển để tránh gãy link nội bộ chưa kiểm chứng.

## Archive-candidate (ghi nhận, CHƯA hành động)
| File | Loại | Lý do | Thay thế | Rủi ro nếu xóa | Khôi phục |
|------|------|-------|----------|----------------|-----------|
| `docs/cleanup/chunks/CHUNK-01..13-summary.md` (13) | doc lịch sử | summary quá trình cleanup đã xong | `docs/cleanup/00-12` tổng hợp | mất chi tiết tiến trình | `git show <sha>:<path>` |
| `docs/cleanup/02-module-maturity-register*.md` (2 nửa) | doc | nên hợp nhất 1 file | — | — | git |

## Thay đổi liên kết (link fix)
| Vị trí | Link cũ (gãy) | Hành động |
|--------|---------------|-----------|
| `README.md` | `docs/v1/GATEWAY-API-V1.md`, `docs/v1/RELEASE-NOTES-v1.md` | Trỏ sang `docs/api/rest-api.md` + `docs/api/grpc-api.md` + `docs/operations/local-development.md` |
| `README.md` | `docs/shared/REPOSITORY-STRATEGY.md` | Gỡ / trỏ `docs/index.md` |
| `README.md` | `docs/README.md`, `docs/backlog/README.md`, `SMARTFARM-VERSION.md` | Trỏ `docs/index.md`, `backlog/README.md`; gỡ version link |

## Script
**Không xóa/archive script nào** — 40 `.sh` đều trỏ module/port có thật, phục vụ build/smoke/release/vận hành (nguyên tắc an toàn §5: không xóa script có khả năng CI/CD/vận hành dùng).

> Mọi khôi phục: `git log --oneline` → `git show <sha>:<path>` hoặc `git checkout <sha> -- <path>`.

← [Documentation Audit](documentation-audit.md)
