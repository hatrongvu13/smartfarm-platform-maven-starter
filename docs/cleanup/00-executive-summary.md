# 00 — Executive Summary — SmartFarm Backlog-Driven Cleanup

**Engagement:** AUDIT → PLAN → APPROVE → CLEANUP → VERIFY → DOCUMENT across the 13-module
SmartFarm monorepo, reconciling backlog V1–V3 against `graphify-out/graph.json`, source,
and the gateway contract surface.

## Outcome in one line
The codebase was **more mature than the backlog implied**; cleanup was hygiene + doc sync,
not deletion. **Zero capability-level removals.** One real prod data-loss bug fixed, two
PARTIAL services brought to the platform bar, one skeleton quarantined, identity security
locked by tests (never downgraded), and the whole reactor builds green.

## What the audit found (Phases 0–5)
- 9 modules PRODUCTION, 3 PARTIAL (inventory tests / health+reporting Flyway), 1 SKELETON
  (simulator). No dead code in `src/main`.
- Divergence was **naming + docs drift** ("work-service" = `order-service`) and **missing
  roadmap planes** (V2 warehouse, V3 automation), not deprecated scope.
- Identity security is conformant and in places **stronger than backlog**.

## What was done (Phase 6–10, approved Wave A–D)
| Item | Result |
|---|---|
| UPD-01 finance prod `create-drop → validate` | data-loss bug fixed |
| UPD-02 inventory JUnit tests | 0 → 7 tests |
| UPD-03 health Flyway + prod `validate` | versioned schema |
| UPD-04 reporting Flyway + prod `validate` | versioned schema |
| UPD-05 reporting download seam | object-storage-ready, contract unchanged |
| UPD-06 reporting report types | placeholders explicit + logged |
| UPD-ID-01/02 admin guards | existing guards **locked by test** |
| UPD-ID-03 / BL-03 prod super-admin | documented runbook |
| SKEL-01 simulator | quarantined `dev & !prod` |
| RISK-CFG-01 compose healthcheck | fixed |
| DOC-01/02/04/05 | backlog ownership + architecture synced to source |

**Verification:** `mvn clean test` → BUILD SUCCESS, every module green, 0 failures/0 errors;
MQTT security tests unchanged (no downgrade).

## What remains (your call, not debt)
- **BL-01 / BL-02** V2 warehouse + V3 automation planes — DEFERRED + documented; building
  them is a new engagement.
- Runtime migrate+validate and MQTT round-trip (needs DB+broker, not started per env rule).
- Graph regeneration (out-of-band collector run).

## Deliverables
`docs/cleanup/00–12` + `chunks/` + `docs/runbooks/prod-super-admin-bootstrap.md`.
