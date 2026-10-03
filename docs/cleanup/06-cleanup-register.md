# 06 — Cleanup Register (Phase 5 — APPROVAL GATE)

> **This is the approval gate.** No Phase 6 cleanup runs until you approve the KEEP /
> UPDATE / REPLACE / REMOVE lists, the module order, and the blockers below.
> Consolidates candidates from Phases 2 (maturity), 3 (traceability), 4 (identity
> security). One source change has already been made this engagement — the finance
> `ddl-auto` data-loss fix (**UPD-01**), applied pre-gate as an urgent bug, not part
> of the batch awaiting approval.
> Labels per item: Classification + Recommended action + Reason + Compatibility +
> Verification + Rollback.

## 0. Headline

The audit found **almost nothing to delete.** The codebase is more mature than the
backlog; the real work is **hygiene (UPDATE), one skeleton (REPLACE-or-quarantine),
and doc/backlog sync (DOCUMENT_ONLY)**, plus two **roadmap BLOCKERS** (missing V2
warehouse plane and V3 automation plane) that are *decisions for you*, not cleanup.
There are **zero capability-level REMOVE** candidates — the backlog supersede chain is
purely additive (Phase 1 FACT).

## 1. KEEP (no change — real, conformant, often stronger than backlog)

| ID | Module / symbol | Reason |
|---|---|---|
| KEEP-01 | libs/common-kernel, security, messaging, proto | Heavily consumed, tested, auto-config-wired; messaging is the ISSUE-04/05 shared infra. |
| KEEP-02 | identity-service (incl. MFA/TOTP/JWT/gRPC policy) | Phase 4 conformant & stronger than backlog. Replay guard, AES-GCM, lockout. |
| KEEP-03 | order-service (persistent saga, outbox, 11 Flyway, projection gap recovery) | Most hardened module; IMPLEMENTED_BETTER than backlog "Work". |
| KEEP-04 | gateway (REST/GraphQL/WS edge, per-service tokens, authz, error mapping) | Stateless edge, uniform `@PreAuthorize`. |
| KEEP-05 | livestock-service (task lifecycle, deadline SLAs, outbox) | Production-shaped. |
| KEEP-06 | finance-service logic (ledger, debt, cash-flow, idempotent saga-compensation) | Real; only its prod config was buggy (UPD-01). |
| KEEP-07 | readiness-service (SSRF-safe bounded probing, dual gRPC+REST, authz) | Small but production-grade. |
| KEEP-08 | gateway `*DevController` facades | Real, auth'd, `@Profile("dev & !prod")`. **NOT demo stubs** — do not remove; see RISK-DEV-01. |
| KEEP-09 | identity FarmDirectory **fail-closed fallback** | Security feature (fails access shut while farm registry unbuilt). Removing it fails open. |

## 2. UPDATE (keep, but fix/finish — scoped, no rewrite)

| ID | Target | Action | Reason | Compat | Verify | Rollback |
|---|---|---|---|---|---|---|
| **UPD-01** ✅ done pre-gate | finance `application-prod.yml` | `ddl-auto: create-drop` → `validate` | Prod schema dropped+recreated every restart (data loss); Flyway owns schema | None (config only; Flyway V1 present) | reactor build + prod-profile start validates against V1 | `git checkout` the one line |
| UPD-02 | inventory-service | Add JUnit tests (ledger concurrency, inbox dedupe, reservation) | PARTIAL: 0 tests | None | `mvn test` green | revert test files |
| UPD-03 | health-service | Add versioned Flyway migrations; prod `ddl-auto: validate` | PARTIAL: Hibernate ddl-auto, no Flyway (breaks "create DB from scratch cleanly") | Needs baseline matching current entities | fresh-DB migrate + validate | drop migrations, restore ddl-auto |
| UPD-04 | reporting-service | Add Flyway + prod `ddl-auto: validate` | PARTIAL: no migrations, prod inherits dev `update` | Baseline from `ExportJobEntity` | fresh-DB migrate | revert |
| UPD-05 | reporting `GetDownloadLocation` | Replace `file://` local path with object-storage (presigned) seam | Not horizontally scalable/durable; code comment says so | API shape stable (still returns a URL) | integration: download via returned URL | revert to local dir |
| UPD-06 | reporting report types | Wire remaining `ReportType`s to real read models (or mark explicitly "summary-only") | Most types render placeholder summary | None if marked; additive if wired | per-type export smoke | revert |
| UPD-ID-01 | identity admin services | Add + test admin peer-protection invariant | Phase 4 item 6 gap | None | unit test: peer modify rejected | revert |
| UPD-ID-02 | identity role mgmt | Verify/guard single-super-admin invariant | Phase 4 item 7 UNKNOWN | None | unit test: 2nd super-admin rejected | revert |
| UPD-ID-03 | identity bootstrap / docs | Define + document **prod** super-admin bootstrap | `DevSuperAdminBootstrap` is dev-only; no prod path | New ops path; do NOT prod-enable dev bootstrap | documented runbook + one-time seed test | n/a (additive) |

## 3. REPLACE (demo/skeleton → real, or quarantine)

| ID | Target | Action | Reason | Compat | Verify | Rollback |
|---|---|---|---|---|---|---|
| SKEL-01 | platform/farm-simulator | **Decision needed:** (a) finish — real `MqttClientFactory` publisher + remove `web-application-type: none`; or (b) quarantine explicitly as dev-only (annotate + exclude from prod reactor profile) | Only DEMO_OR_SKELETON found: HTTP emit is a 202 stub publishing nothing, observer is log-only, web layer disabled by config | It's dev-only; no prod consumer depends on it | if finished: publish→observe round-trip; if quarantined: build excludes it from prod | revert |

**REPLACE rule honoured:** no demo path is removed before a replacement compiles; the
simulator is the only candidate and the default is **quarantine** (safe) unless you
want it finished.

## 4. REMOVE (strictly gated — currently EMPTY)

**No REMOVE candidates meet all six conditions.** Specifically:
- `*DevController` facades — have callers (dev), are profile-gated → **KEEP-08**, not REMOVE.
- `FarmDirectoryService` proto (no server impl) — backed by a deliberate fail-closed
  fallback and a V3/farm-registry seam → **DOCUMENT**, not REMOVE (removing the proto
  would break the identity fallback wiring).
- `OrderSagaOrchestrator` — already removed from source (ISSUE-03); only stale graph +
  docs mention it → handled by DOC-03 (doc cleanup) + graph regen, no code REMOVE.

If you want any of these treated as REMOVE, say so explicitly — the audit's position is
that none qualify under the stop-rules.

## 5. DOCUMENT_ONLY (source is right; backlog/docs/config lag)

| ID | Target | Action |
|---|---|---|
| DOC-01 | backlog + `SERVICE-OWNERSHIP.md` + architecture map | Record "Work/Task" owner = `smartfarm-order-service` (undocumented rename). |
| DOC-02 | architecture / security docs | Record FarmDirectory is an intentional fail-closed stub pending a farm registry. |
| DOC-03 | docs mentioning `OrderSagaOrchestrator` | Remove/replace with the persistent-worker saga description (code already changed). |
| DOC-04 | README / architecture | Add `apps/smartfarm-web` and `platform/smartfarm-readiness-service` (UNMAPPED reverse gaps). |
| DOC-05 | backlog V2/V3 | Annotate CAP-WAREHOUSE-* and all V3 automation caps as **NOT IMPLEMENTED** (see BLOCKERS). |

## 6. BLOCKERS (your decision — not cleanup, roadmap scope)

| ID | What | Why it's a blocker |
|---|---|---|
| **BL-01** | V2 warehouse plane MISSING (StorageLocation / QR / put-away-pick) | Cannot mark V2 "done". Decide: **defer+document** vs **build**. Blocks any V2 completion claim. |
| **BL-02** | V3 automation plane MISSING (device registry / telemetry persist / rule engine / actuator control) | Only a skeleton simulator exists. Decide: **defer+document** vs **build**. Blocks V3 completion claim. |
| **BL-03** | Prod super-admin bootstrap undefined (UPD-ID-03) | A real deployment cannot create its first admin. Blocks production launch. |

## 7. RISK register (watch, not actions)

- **RISK-DEV-01**: the `dev` Spring profile must never be active in a prod deploy — it
  is the only thing exposing `*DevController` facades and the dev super-admin bootstrap.
  Verify in deploy config (Phase 9).
- **RISK-BROKER-01**: `infra/mosquitto.conf allow_anonymous true` + open `ws:9001`.
  App-layer HMAC mitigates integrity, not broker access. Mark prod broker config
  (Phase 9).
- **RISK-CFG-01**: `compose.yaml` healthcheck user mismatch (`pg_isready -U smartfarm`
  vs superuser `postgres`); dev-only, misleading.
