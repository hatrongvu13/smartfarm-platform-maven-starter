# Documentation Audit — SmartFarm Platform

> **Nguồn sự thật**: source code hiện tại > runtime/deploy config > gateway/controller/gRPC/client > migration/schema/event contract > Graphify > README/docs > issue/history.
> **Phạm vi**: toàn repo. **Ngày**: 2026-10-06. **HEAD**: `eaaa112` (update set active profile default).
> **Phương pháp**: inventory + đối chiếu trực tiếp source (REST/GraphQL/gRPC/proto/outbox/migration/config) qua 4 scan song song, mọi kết luận trạng thái đều kèm bằng chứng từ source. Nhận định chưa xác minh được đánh dấu `NEEDS_VERIFICATION`.

---

## 1. Tổng quan module (14 reactor module + 1 root)

Root `pom.xml` khai báo **14 module** (README nói "13" — **DISCREPANCY**, README đếm thiếu; đã liệt kê đủ dưới đây).

| # | Module | Loại | Có main class | Trạng thái implementation | Bằng chứng |
|---|--------|------|:---:|------|------|
| 1 | `libs/smartfarm-common-kernel` | lib | — | IMPLEMENTED | shared value objects/util |
| 2 | `libs/smartfarm-security` | lib | — | IMPLEMENTED | JWT validator, GrpcMethodPolicy, HMAC envelope |
| 3 | `libs/smartfarm-messaging` | lib | — | IMPLEMENTED | MQTT connection/outbox primitives |
| 4 | `libs/smartfarm-proto` | lib | — | IMPLEMENTED | 12 `.proto`, 9 có service |
| 5 | `platform/smartfarm-readiness-service` | platform | ✅ | IMPLEMENTED | `ReadinessGrpcService` + `ReadinessController` |
| 6 | `platform/smartfarm-farm-simulator` | platform | ✅ | SKELETON (quarantined `@Profile dev&!prod`) | `SimulationController` dev-only; không có `application-dev.yml` |
| 7 | `services/smartfarm-livestock-service` | service | ✅ | IMPLEMENTED | `LivestockGrpcService`, outbox relay |
| 8 | `services/smartfarm-health-service` | service | ✅ | PARTIAL | `HealthGrpcService` 4/10 RPC; **không có gateway reach** |
| 9 | `services/smartfarm-inventory-service` | service | ✅ | IMPLEMENTED | `InventoryGrpcService`, MQTT subscriber, 2 migration |
| 10 | `services/smartfarm-identity-service` | service | ✅ | IMPLEMENTED | 5 gRPC service, auth/MFA/RBAC, outbox |
| 11 | `services/smartfarm-finance-service` | service | ✅ | PARTIAL | `FarmFinanceGrpcService`; chỉ 2 GraphQL dev-only expose |
| 12 | `services/smartfarm-order-service` | service | ✅ | COMPLETE | saga bền + outbox + CQRS, 11 migration |
| 13 | `services/smartfarm-reporting-service` | service | ✅ | PARTIAL | `ReportingGrpcService`; non-livestock report types chưa đủ |
| 14 | `apps/smartfarm-gateway` | app (edge) | ✅ | IMPLEMENTED | REST + GraphQL edge, gRPC fan-out |

---

## 2. Tài liệu — inventory & action

**Tổng: 83 file `.md`** trong `docs/` + `backlog/`. Phân loại:

### 2.1 Tài liệu chính xác & còn dùng (KEEP)
- `docs/audit/00-executive-summary.md` … `08-*.md` — bộ audit gốc, còn giá trị, reference được.
- `docs/cleanup/00-12` + `docs/cleanup/chunks/CHUNK-01..13` — engagement cleanup đã xong.
- `docs/operations/ORDER-*.md` (3) — runbook/SLO/acceptance cho order, current.
- `docs/architecture/order-saga.md` — mô tả saga, current.
- `docs/refactor/DB-OWNERSHIP.md`, `DEV-DB-WORKFLOW.md` — current.
- `docs/remediation/ISSUE-03-*.md`, `ISSUE-04-05-*.md` — kết quả remediation, lịch sử giá trị.
- `docs/runbooks/prod-super-admin-bootstrap.md` — current.
- `backlog/V1-Core-Platform/*`, `V2-*`, `V3-*`, các README/strategy — backlog versioned, current.
- `docs/PROJECT-STATUS.md`, `docs/07/10/11-*.md`, `docs/audit/graphify/*`, `repository-manifest.md`, `audit-progress.md` — bộ incremental 2026-10-03.

### 2.2 Tài liệu cần CẬP NHẬT (UPDATE)
| File | Vấn đề | Action |
|------|--------|--------|
| `README.md` | **6/8 internal link gãy** (xem §5); đếm module "13" sai (đúng 14) | UPDATE — sửa link, sửa count, trỏ Documentation Index mới |
| `docs/audit/04-issue-register.md` | ISSUE-01 & ISSUE-19/CFG-01 ghi `OPEN` nhưng **source đã RESOLVED** | UPDATE — xem §4 |
| `docs/security/mqtt-acl-mtls.md` | mô tả ACL/mTLS như sample; HMAC envelope thực tế đã impl ở `libs/smartfarm-security` (default-off) — doc drift | UPDATE — làm rõ "sample config vs implemented app-level HMAC" |
| `docs/cleanup/09-documentation-config-sync.md` | tuyên bố README OK, nhưng README đang gãy link | UPDATE/annotate — mâu thuẫn với thực tế |
| `docs/PROJECT-STATUS.md` | CFG-01 ghi blocker; đã RESOLVED | UPDATE completion matrix |

### 2.3 Tài liệu trùng lặp (MERGE candidate)
- `docs/cleanup/02-module-maturity-register.md` + `02-module-maturity-register-part2.md` → hợp nhất 1 file.

### 2.4 Tài liệu archive candidate (giá trị lịch sử, không active)
- `docs/cleanup/chunks/CHUNK-01..13-summary.md` (13 file) — summary quá trình, giữ lịch sử nhưng archive khỏi active docs. **CHƯA xóa** (giá trị lịch sử).

### 2.5 Tài liệu đề xuất XÓA
- **Không có file đề xuất xóa trong đợt này.** Tất cả hoặc current, hoặc archive-candidate (giữ lịch sử). Nguyên tắc an toàn §2: không xóa chỉ vì cũ.

---

## 3. Script — inventory (40 `.sh`)

**Kết luận: tất cả script trỏ tới module/port có thật — không có phantom service.** Không script nào đề xuất xóa.

| Nhóm | File | Trạng thái | Action |
|------|------|-----------|--------|
| Toolchain | `scripts/doctor.sh`, `bootstrap.sh`, `build-all.sh`, `run-all.sh`, `config-forward.sh` | current | KEEP |
| Release v1 | `scripts/release/v1/*.sh` (10) | current, dùng cho smoke v1 | KEEP |
| Release prod | `scripts/release/production/validate-order-release.sh` | current | KEEP |
| REST tests | `scripts/rest/**/*.sh` (6) | current | KEEP |
| GraphQL tests | `scripts/graphql/**/*.sh` + `.graphql` | current | KEEP |
| Phase curls | `scripts/phase2-curl-test.sh`, `phase3-saga-curl-test.sh` | current nhưng target endpoint **dev-only** | KEEP + annotate "dev profile only" |
| Dev curls | `scripts/dev/curl-06-*.sh`, `curl-07-*.sh`, `rebuild-restart.sh` | current | KEEP (merge candidate: gộp curl-06/07) |
| Livestock | `scripts/livestock-*-test.sh` (2) | current | KEEP |
| Lib | `scripts/lib/*.sh` (4) | shared helper | KEEP |
| Smoke/health | `scripts/smoke/smoke-test.sh`, `health/test-health.sh` | current | KEEP |
| Legacy | `scripts/legacy/README.md` | marker | KEEP |

---

## 4. Issue — đã giải quyết bởi source vs còn tồn đọng

**Đối chiếu trực tiếp `docs/audit/04-issue-register.md` với source hiện tại (source WINS).**

### 4.1 Đã RESOLVED (source đã sửa) — bỏ khỏi active checklist
| ID | Register nói | Source thực tế | Bằng chứng |
|----|------|------|------|
| **ISSUE-01** (CRITICAL) | OPEN | **RESOLVED** | 7 service base + prod `application.yml` dùng nested `smartfarm.security.jwt.{issuer,jwk-set-uri,audiences}` |
| **ISSUE-19 / CFG-01** (HIGH) | OPEN | **RESOLVED** | cả 7 `application-test.yml` đã chuyển sang nested `jwt:` form (hết flat key) |
| ISSUE-03, 04, 05, 08, 09, 14 | RESOLVED | RESOLVED (xác nhận) | khớp register |

> Ngoài ra: cấu hình profile đã đồng bộ — 9/9 module runnable dùng `active: ${SPRING_PROFILES_ACTIVE:dev}` (commit `eaaa112`). Lỗi "Unable to determine Dialect" không còn tái diễn khi chạy mặc định.

### 4.2 Còn OPEN (có bằng chứng, cần theo dõi) — xem `docs/audit/unresolved-items.md`
ISSUE-02 (MQTT HMAC wiring, PARTIAL), ISSUE-06 (identity.command.result orphan), ISSUE-07 (dup gRPC autoconfig), ISSUE-10 (publisher cleanSession), ISSUE-11 (gRPC audience per-service), ISSUE-12 (super-admin SCOPE bypass), ISSUE-13 (timeout/circuit-breaker), ISSUE-15 (ddl-auto dev/prod), ISSUE-16 (dev secret trong repo).

### 4.3 INFO (không phải lỗi)
ISSUE-14, 17, 18.

---

## 5. README link check (BROKEN LINKS — ưu tiên cao)

| Link trong README | Tồn tại? |
|-------------------|:--------:|
| `./SMARTFARM-VERSION.md` | ❌ BROKEN |
| `./docs/README.md` | ❌ BROKEN |
| `./docs/backlog/README.md` | ❌ BROKEN (đúng là `backlog/README.md`) |
| `./docs/v1/GATEWAY-API-V1.md` (+ 2 anchor) | ❌ BROKEN (thư mục `docs/v1/` không tồn tại) |
| `./docs/v1/RELEASE-NOTES-v1.md` | ❌ BROKEN |
| `./docs/shared/REPOSITORY-STRATEGY.md` | ❌ BROKEN (thư mục `docs/shared/` không tồn tại) |
| `./docs/operations/ORDER-OPERATIONS-RUNBOOK.md` | ✅ OK |
| `./docs/operations/ORDER-PRODUCTION-ACCEPTANCE.md` | ✅ OK |

→ README phải được viết lại trỏ tới `docs/index.md` + cấu trúc mới. Giữ nguyên nội dung Quick Start đã xác minh.

---

## 6. Các điểm chưa thể xác minh (NEEDS_VERIFICATION)

- **Build/test thực tế**: Maven không có trên PATH, `MAVEN_HOME` chưa set (preference người dùng: hỏi trước khi chạy Maven). Toàn bộ kết luận implementation dựa trên đọc source, **không** có log build/test lần này.
- **farm.proto / telemetry.proto**: định nghĩa nhưng không có server impl — PLANNED (V3), không coi là BROKEN.
- **Prod runtime end-to-end**: chưa chạy; kết luận "RUNNABLE (conditional)" dựa trên config, không phải khởi chạy thực.
