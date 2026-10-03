# 12 — Final Project State (Phase 10)

Per-module conclusion after the approved Wave A–D cleanup. Verdicts:
`PRODUCTION_READY` / `PARTIAL_WITH_BACKLOG` / `DEMO_REMOVED` / `DEFERRED` / `BLOCKED`.

| Module | Verdict | Basis |
|---|---|---|
| libs/common-kernel | PRODUCTION_READY | Tested shared kernel; untouched. |
| libs/security | PRODUCTION_READY | JWT + HMAC MQTT; 27 tests; no downgrade. |
| libs/messaging | PRODUCTION_READY | ISSUE-04/05 infra; 12 tests. |
| libs/proto | PRODUCTION_READY | Generated contracts. |
| identity-service | PRODUCTION_READY | 55 tests incl. new delegation-guard; prod super-admin bootstrap now documented (runbook). |
| order-service | PRODUCTION_READY | Persistent saga, 11 Flyway, 22 tests. |
| gateway | PRODUCTION_READY | Edge tier; dev facades profile-gated (confirm `dev` off in prod). |
| livestock-service | PRODUCTION_READY | Task lifecycle + outbox. |
| finance-service | PRODUCTION_READY | **Prod data-loss bug fixed** (UPD-01); real ledger + idempotent saga compensation. |
| inventory-service | PRODUCTION_READY | Was PARTIAL; now has JUnit coverage (UPD-02). Domain/gRPC/MQTT inbox real. |
| health-service | PRODUCTION_READY | Was PARTIAL; now Flyway-owned schema + prod `validate` (UPD-03). |
| reporting-service | PARTIAL_WITH_BACKLOG | Flyway added (UPD-04); download seam added (UPD-05); non-livestock report types explicitly placeholder + logged (UPD-06). Remaining: wire real read-models for the other report types + a prod object-storage resolver bean. |
| readiness-service | PRODUCTION_READY | SSRF-safe probing; untouched. |
| farm-simulator | DEFERRED (quarantined) | DEMO_OR_SKELETON; `@Profile("dev & !prod")` enforced (SKEL-01). Finishing it = net-new feature. |
| apps/smartfarm-web | PARTIAL_WITH_BACKLOG | UI app; now documented in ownership map (DOC-04). |

## Deferred roadmap items (your BLOCKERS — not code debt)
- **BL-01 — V2 warehouse plane** (StorageLocation / QR / put-away-pick): **DEFERRED**, annotated
  NOT_IMPLEMENTED in `backlog/SERVICE-OWNERSHIP.md`. Building it is a new engagement.
- **BL-02 — V3 automation plane** (device registry / telemetry / rule engine / actuator):
  **DEFERRED**, annotated NOT_IMPLEMENTED. New engagement.
- **BL-03 — prod super-admin bootstrap**: resolved to a documented runbook (UPD-ID-03). Pick the
  mechanism (one-time `bootstrap` profile job vs operator seed) at deploy time.

## Residual RISK (watch)
- **RISK-DEV-01**: ensure the `dev` Spring profile is never active in prod (gates dev facades +
  dev super-admin bootstrap). Verify in deploy manifests.
- **RISK-BROKER-01**: prod MQTT broker needs `allow_anonymous false` + ACL + TLS decision
  (`10-mqtt-production-readiness.md`).

## Definition of Done — status
- [x] Backlog consolidated + traced to source (01, 03).
- [x] Real implementation kept and optimized, not rewritten (KEEP list honoured).
- [x] Identity/TOTP/admin security reviewed; strengthened by tests, never downgraded (05).
- [x] Demo/skeleton handled (simulator quarantined; no silent dead code).
- [x] Gateway/gRPC/MQTT/WS contracts coherent (04, 10).
- [x] Scripts registered with evidence (08).
- [x] Docs/config synced to source (09, ownership map).
- [x] MQTT prod config marked (10).
- [x] Reactor compiles + all tests pass (11).
- [ ] Graph regenerated — deferred to an out-of-band collector run.
