# Audit Progress — Checkpoint Log

> Theo dõi tiến độ audit theo boundary (spec §33/§34). Mỗi lần audit ghi 1 block.
> Audit này là **INCREMENTAL** trên baseline đã có (`docs/audit/00-08`, `docs/cleanup/00-12`).

## Mode của lần chạy hiện tại

```
MODE:            INCREMENTAL_AUDIT
Date:            2026-10-03
Branch:          main
HEAD commit:     51f23a7  "v1 start"  (2026-10-03 16:56 +07)
Graph snapshot:  graphify-out/graph.json  (Oct 2 15:56, 4509 nodes / 13727 edges)
Change scope:    CONFIG + TEST only (no src/main .java/.sql/.proto diff)
Graphify rescan: NOT_REQUIRED (cấu trúc call/dependency không đổi)
```

## Change detection (spec §6)

Delta kể từ graph snapshot (commit `51f23a7` "v1 start"):

| Loại thay đổi | Nội dung | Ảnh hưởng graph |
|---|---|---|
| Added config | `application-test.yml` cho 7 service (finance/health/identity/inventory/livestock/order/reporting) | Không (runtime config, không phải node/edge) |
| Modified config | `application-dev.yml` / `application-prod.yml` tinh chỉnh nhỏ ở vài service | Không |
| Added test | `AbstractInventoryPostgresIT` + `InventoryReceiveMovementIT` (staged); identity ITs (`AbstractIdentityPostgresIT`, `FarmAuthorizationIT`) + nhiều `ProdSchemaSafetyTest`, domain tests (untracked) | Không (test không nằm trong graph `src/main`) |
| Source `src/main` | **KHÔNG CÓ** thay đổi `.java` thực sự trong commit (mtime dịch do checkout) | — |

Kết luận: `INCREMENTAL_AUDIT` — chỉ cần audit lại tầng **CONFIG + TEST**; kiến trúc/dependency graph giữ nguyên.

## Boundary checkpoints

### Boundary: CONFIG (runtime security properties)
```
Status:              AUDITED
Files scanned:       7 × application-test.yml, record SmartFarmSecurityProperties.java
Modules scanned:     finance, health, identity, inventory, livestock, order, reporting
Graphify snapshot:   reuse (unchanged)
Findings:            CFG-01 (NEW, HIGH) — 6/7 application-test.yml dùng flat security key
                     => latent boot-blocker dưới profile `test` (lặp lại pattern ISSUE-01)
Blocked deps:        none
Confidence:          HIGH
```

### Boundary: TEST (new integration tests)
```
Status:              AUDITED
Files scanned:       Abstract*PostgresIT (inventory/identity/finance), *IT, ProdSchemaSafetyTest
Findings:            TEST-01 (INFO) — ITs mới dùng Testcontainers/EXTERNAL Postgres, gated bằng
                     -Dit.postgres.enabled=true; ghi đè security bằng nested jwt.* đúng chuẩn
                     INLINE => IT tự boot OK, KHÔNG dính CFG-01.
                     Coverage tăng: inventory + identity + finance có IT thật.
Confidence:          HIGH
```

### Boundary: ARCHITECTURE / DEPENDENCY / API-PROTO / EVENT-FLOW / SECURITY / RUNTIME
```
Status:              UNCHANGED (carry-forward từ baseline)
Reference:           docs/audit/01-architecture-graph-report.md
                     docs/audit/02-event-flow-report.md
                     docs/audit/03-business-workflow-report.md
                     docs/audit/04-issue-register.md
                     docs/cleanup/02-module-maturity-register*.md
                     docs/cleanup/12-final-project-state.md
Confidence:          HIGH (đã audit FULL ở các pass trước)
```

## Next boundary
Không còn boundary nào thay đổi cần quét. Audit incremental **COMPLETE** cho delta này.
Lần sau: nếu `src/main` .java/.sql/.proto đổi → chạy lại `graphify` và cập nhật `graphify/graph-diff.md`.
