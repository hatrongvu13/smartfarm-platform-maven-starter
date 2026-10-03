# 07 — Execution Plan (Phase 5 — pending approval)

> The order Phase 6+ will run **once you approve `06-cleanup-register.md`**. Nothing
> here executes before the gate. Each step lists its register ID, the vertical-slice
> order (config → entity → repo/service → contracts → gateway → verify), and a
> per-step compile/verify so no demo path is removed before its replacement builds.

## Guiding constraints (from the prompt's Phase 6 rules)
- No demo removed before replacement compiles. No two sources of truth in parallel.
- No Order/Identity/Gateway contract change without a version/adapter.
- No shared framework from a single use case. (messaging lib already exists & justified.)
- Flyway/schema must build a fresh DB cleanly; squash only with explicit reset consent.
- `MAVEN_HOME` = `/Users/jaxmac/sdk/apache-maven-3.9.16` (JDK17) — confirm before any build.

## Wave A — low-risk hygiene (parallel-safe, no contract change)
Order chosen so the schema-management fixes (fresh-DB rule) land first.

1. **UPD-01** ✅ *already done* — finance `ddl-auto` → `validate`. (verify in Wave D build)
2. **UPD-03** health Flyway baseline + prod `validate`. Verify: drop+create DB, migrate, `validate` passes, `HealthOutboxRelayTest` green.
3. **UPD-04** reporting Flyway baseline + prod `validate`. Verify: fresh-DB migrate; export job round-trip.
4. **UPD-02** inventory JUnit tests (ledger concurrency / inbox dedupe / reservation). Verify: `mvn -pl services/smartfarm-inventory-service test`.
5. **DOC-03** remove `OrderSagaOrchestrator` from docs (code already clean).

## Wave B — identity security UPDATEs (no rewrite, guarded)
Serialized (same module, security-sensitive — one writer).

6. **UPD-ID-01** admin peer-protection guard + test.
7. **UPD-ID-02** single-super-admin invariant verify/guard + test.
8. **UPD-ID-03** define + document prod super-admin bootstrap (ops runbook + one-time seed path; dev bootstrap stays `dev & !prod`).
   Verify Wave B: `mvn -pl services/smartfarm-identity-service test`; security tests (incl. MQTT 8) stay green — **no downgrade**.

## Wave C — reporting completion + simulator decision
9. **UPD-05** reporting download → object-storage seam (keep URL contract shape).
10. **UPD-06** wire or explicitly mark remaining report types.
11. **SKEL-01** simulator: **(a) finish** (real publisher + drop `web-application-type: none`) **or (b) quarantine** (annotate dev-only, exclude from prod profile) — per your choice at the gate. Default = quarantine.
    Verify: if finished, publish→observe round-trip; if quarantined, prod reactor profile excludes it.

## Wave D — docs/config/graph sync + full verification (Phases 7-10)
12. **DOC-01/02/04/05** backlog + architecture + ownership sync (order rename, FarmDirectory stub, web/readiness modules, V2/V3 NOT-IMPLEMENTED annotations).
13. Phase 7 script register (`08-script-register.md`): fix `compose.yaml` healthcheck user (RISK-CFG-01), document dev secrets, merge/retire ad-hoc curl harnesses.
14. Phase 9 MQTT readiness marking (`10-mqtt-production-readiness.md`): broker `allow_anonymous`, ACL/mTLS as optional/required, typed-property reconciliation against real `@ConfigurationProperties` keys, RISK-DEV-01 prod-profile check.
15. Phase 10 verify: regenerate graph; `"$MAVEN_HOME/bin/mvn" -DskipTests compile` then `mvn test`; confirm no dead nodes, no unbound properties, no dangling script/doc references; write `11-verification-result.md` + `12-final-project-state.md` (per-module PRODUCTION_READY / PARTIAL_WITH_BACKLOG / DEMO_REMOVED / DEFERRED / BLOCKED).

## BLOCKER decisions needed BEFORE their waves
- **BL-01 / BL-02** (V2 warehouse, V3 automation): defer+document vs build. If *defer*, they become DOC-05 annotations and Wave D proceeds. If *build*, that is a new engagement, not this cleanup.
- **BL-03** (prod super-admin bootstrap): resolved by UPD-ID-03 in Wave B — confirm the mechanism you want (one-time migration seed vs ops runbook).

## Suggested approval granularity
You can approve the whole plan, or approve **Wave A + B only** (pure hygiene +
security, zero roadmap risk) and hold C/D pending the BL-01/BL-02 defer-vs-build call.
