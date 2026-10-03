# 10 — Implementation Roadmap

> Spec §27/§29. Runtime-first. Không triển khai feature mới nếu phụ thuộc nền tảng đang BROKEN.
> Baseline remediation chi tiết: `docs/audit/06-remediation-plan.md`. File này là lớp roadmap tổng + delta mới.

```
CURRENT STATE
  prod RUNNABLE (ISSUE-01 vá) · test profile BLOCKED (CFG-01) · MQTT integrity default-off
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
  ISSUE-02 nối MQTT HMAC verifier vào publisher/consumer · broker allow_anonymous false + ACL + TLS (RISK-BROKER-01)
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
| P1 | env/prod confirm, broker hardening | prod boot + health UP + MQTT auth bật |
| P2 | ISSUE-02 wiring, ISSUE-06, IT | HMAC verify-enabled rollout; command-result có consumer; IT CI xanh |
| P3 | reporting, coupling | report types thật; không regression |
| P4 | V2/V3 | new engagement riêng |

Không đổi version hiện tại của repo (V1/V2/V3 backlog giữ nguyên). Delta mới dùng `backlog/v0.1/`.
