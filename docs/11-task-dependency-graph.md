# 11 — Task Dependency Graph

> Spec §28. DEPENDS ON / BLOCKS / UNBLOCKS. Runtime-first order.

## v0.1 (incremental delta — chặn CI/test)
```
V0.1-001  (CFG-01: vá application-test.yml x6 → nested jwt.*)
   DEPENDS ON:  —
   BLOCKS:      mọi test chạy với profile `test`; CI reproducible
   UNBLOCKS:    V0.1-002 (chạy full test suite trong CI)
      │
      ▼
V0.1-002  (bật IT trong CI: -Dit.postgres.enabled=true + Testcontainers)
   DEPENDS ON:  V0.1-001
   BLOCKS:      gate "all tests pass" cho release
```

## Carry-forward (baseline — chi tiết trong docs/audit/04 + 06)
```
ISSUE-01  (order prod YAML)   STATUS: FIXED  → không còn trên critical path
ISSUE-02  (MQTT HMAC wiring)  P2   DEPENDS ON: broker hardening decision (RISK-BROKER-01)
ISSUE-06  (identity command-result orphan)  P2  độc lập
ISSUE-03  (dual saga key)     P3   DEPENDS ON: quyết định hợp nhất saga path (UNKNOWN — cần user)
ISSUE-04/05 (HoL blocking / consumer persistence)  P3  độc lập, không chặn runtime
BL-01 V2 warehouse  P4  DEPENDS ON: V1 lock (identity/event/coordinate/task/device contract)
BL-02 V3 automation P4  DEPENDS ON: BL-01 + device/telemetry contract
```

## Critical path (runtime)
```
V0.1-001 ──unblocks──▶ V0.1-002 ──enables──▶ CI release gate
   (prod đã RUNNABLE độc lập với 2 task trên vì ISSUE-01 đã vá)
```

## One-way-door (cần user quyết — spec: không tự chọn)
- **ISSUE-03 saga path consolidation** — hợp nhất 2 đường idempotency-key (`:expense` vs `:finance`) là lựa chọn kiến trúc; chờ user.
- **BL-01 / BL-02** — new engagement, không khởi động trong audit.
