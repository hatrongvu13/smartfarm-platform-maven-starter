# 10 — Implementation Roadmap

> Spec §27/§29. Runtime-first. Không triển khai feature mới nếu phụ thuộc nền tảng đang BROKEN.
> Baseline remediation chi tiết: `docs/audit/06-remediation-plan.md`. File này là lớp roadmap tổng + delta mới.

```
CURRENT STATE (updated 2026-10-07)
  prod RUNNABLE (ISSUE-01 vá) · MQTT integrity ON (HMAC SFM1 + protobuf, live-verified)
  gateway nối health (GW-01) · health prod event path (EVT-03) · identity events protobuf (EVT-01/02)
        │
        ▼
P0 BLOCKERS
  V0.1-001  CFG-01  vá 6 application-test.yml → nested jwt.* (chặn CI/test profile)
        │
        ▼
MINIMUM RUNNABLE SYSTEM
  xác nhận env prod đầy đủ · Flyway validate pass · gateway↔service gRPC + JWKS
        │
        ▼
CORE FEATURES  (đã COMPLETE — giữ, không rewrite)
  order saga · identity auth/RBAC/MFA · finance ledger · inventory/livestock/health domain
        │
        ▼
TEST / VALIDATION
  mở rộng IT cross-service · contract test proto · chạy IT với -Dit.postgres.enabled=true trong CI
        │
        ▼
OBSERVABILITY
  observability stack đã có → gắn alerting SLO (docs/operations/ORDER-SLO.md)
        │
        ▼
SECURITY HARDENING
  ISSUE-02 MQTT HMAC verifier ĐÃ nối publisher/consumer (ký+verify, live 5/5) · CÒN: broker allow_anonymous false + ACL + TLS (RISK-BROKER-01, ISSUE-15/16)
        │
        ▼
REFACTORING
  giảm coupling order god-nodes (OrderEntity deg 94) — chỉ khi có bằng chứng bottleneck
        │
        ▼
OPTIMIZATION
  bỏ head-of-line blocking relay/dispatcher (ISSUE-04) · consumer persistence (ISSUE-05)
        │
        ▼
EXTENDED FEATURES
  reporting non-livestock read-models · V2 warehouse (BL-01) · V3 automation (BL-02)
```

## Phase gates
| Phase | Scope | Done-criteria |
|---|---|---|
| P0 | V0.1-001 | `SPRING_PROFILES_ACTIVE=test` boot OK ở cả 6 service; `mvn test` xanh |
| P1 | env/prod confirm, broker hardening | prod boot + health UP + MQTT auth bật (HMAC app-level ✅ live; broker ACL/TLS còn OPEN) |
| P2 | ISSUE-02 wiring ✅, ISSUE-06 ✅, IT | HMAC verify-enabled rollout ✅ live-verified; command-result reclassified by-design (WS-consumed); IT CI xanh (còn) |
| P3 | reporting, coupling | report types thật; không regression |
| P4 | V2/V3 | new engagement riêng |

Không đổi version hiện tại của repo (V1/V2/V3 backlog giữ nguyên). Delta mới dùng `backlog/v0.1/`.
