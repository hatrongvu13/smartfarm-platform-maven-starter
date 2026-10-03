# 03 — Backlog ↔ Implementation ↔ Gateway Traceability (Phase 3)

> **AUDIT-ONLY.** No source edited while producing this (the one source change this
> engagement made — finance `ddl-auto` — is an approved pre-gate bug fix, tracked in
> `06-cleanup-register.md` as UPD-01, not a Phase 3 action).
> Inputs: `01-backlog-version-consolidation.md`, `02-module-maturity-register.md`
> (+ part 2), `04-gateway-contract-coverage.md`, plus direct source verification of
> the Phase-1 Farm/Spatial BLOCKER (see §3).
> Labels: **FACT / INFERENCE / UNKNOWN / RISK / BLOCKER** and reconciliation status
> **MATCHED / IMPLEMENTED_BETTER / PARTIAL / MISSING / OBSOLETE / UNMAPPED / CONFLICTED**.

---

## 1. Resolution of the Phase-1 BLOCKER (Farm / Spatial / V3)

Phase 1 could not tell, from the backlog alone, whether Farm/Zone/Spatial and the V3
automation plane were *folded into another module* or *missing*. Resolved this phase
by direct source search (FACT):

- **No** `FarmEntity` / `ZoneEntity` / `AreaEntity` / `SpatialObjectEntity` /
  `StorageLocationEntity` / `WarehouseEntity` / `RackEntity` / `BinEntity` exists in
  any module. The only `Farm*` symbols are **naming** (`FarmOrderService`,
  `FarmFinanceService`, `FarmGraphQlController`) or **authorization scope**
  (`identity/.../FarmScopeService`). (FACT)
- `FarmDirectoryService` (proto `farm.v1`) has **no server impl**; identity ships a
  deliberate **fail-closed** `FarmDirectoryFallbackConfiguration` whose
  `FarmDirectoryPort.existsInTenant(...)` returns `false` and logs
  *"Remove this configuration when a real FarmDirectoryPort implementation is
  connected."* (FACT)
- **No** `Device*` / `Telemetry*` / `Sensor*` / `Actuator*` / `RuleEngine*` class
  anywhere in `services`/`libs`/`platform`. (FACT)

**Conclusion (FACT, un-blocks Phase 3):** Farm/Spatial/StorageLocation and the whole
V3 automation plane are **MISSING**, not folded elsewhere. The farm-directory gap is
**safe** (fail-closed, documented), not a silent dead contract. V1's "spatial frozen"
gate item therefore sits on an **unbuilt** foundation — but because the design is a
seam (`StorageLocationRef`) and nothing downstream dereferences a concrete storage
location, the missing foundation does not break the services that exist. It is a
backlog/scope gap for your decision, not a build-breaker.

---

## 2. Traceability matrix (capability → effective version → owner → contracts → status)

Contracts verified from source (`04-gateway-contract-coverage.md` + CHUNK-11/12).
"Owner (actual)" is the real module; "—" means no module owns it.

| Capability | Eff | Owner (actual) | REST (gw) | GraphQL | gRPC | MQTT | Status |
|---|---|---|---|---|---|---|---|
| CAP-IDENTITY-CORE/RBAC/S2S-AUTH | V1 | identity + libs/security | `/api/v1/auth/*`, `/whoami` | `users/roles/permissions/userAuthorization` | 4 identity svcs | command intake/result | **MATCHED** |
| CAP-EVENT-ENVELOPE / OUTBOX / IDEMPOTENCY / AUDIT | V1 | libs/common-kernel + libs/messaging | — | — | — | outbox/inbox everywhere | **IMPLEMENTED_BETTER** (durable saga, poison classifier, gap recovery beyond backlog) |
| CAP-INVENTORY-LEDGER | V1 | inventory | via dev-setup (dev) | `warehouseInventory`,`lowStock`,`dashboard` | `InventoryService` | consumes task-changed | **MATCHED** (RISK: 0 tests) |
| CAP-WORK-TASK | V1 | **order-service** (renamed) | `/api/v1/orders`, `/order-sagas/*` | `orders`,`order`,`orderSaga` + mutations | `FarmOrderService`,`OrderSagaAdmin` | publishes order-changed; consumes | **IMPLEMENTED_BETTER** + **CONFLICTED naming** (backlog "Work"/"Task" → module "order") |
| CAP-LIVESTOCK | V1 | livestock | `/api/v1/livestock/*` (+ dev task CRUD) | `tasks`,`task` | `LivestockTaskService` | publishes task events | **MATCHED** |
| CAP-HEALTH | V1 | health | — (no gw edge) | — | `AnimalHealthService` | publishes observation-recorded | **PARTIAL** (no Flyway, ddl-auto; no gateway edge — UNKNOWN if intended internal-only) |
| CAP-FINANCE-LEDGER | V1 | finance | — | `batchCost`,`cashFlow`,`dashboard` | `FarmFinanceService` | — | **MATCHED** (RISK: prod `create-drop` — UPD-01, fixed pre-gate) |
| CAP-REPORTING | V1 | reporting | `/api/v1/reports/*` (**dev** facade) | — | `ReportingService` | — | **PARTIAL** (only livestock report type real; others placeholder; no Flyway; `file://` downloads) |
| CAP-DEVICE-CONTRACT / TELEMETRY-ENVELOPE / COMMAND-ENVELOPE | V1 | libs/proto (+ messaging envelope) | — | — | proto schemas | HMAC command/event envelope | **PARTIAL** (envelope + topic contract exist; device/telemetry *domain* schemas unconfirmed as first-class proto) |
| CAP-SPATIAL-FOUNDATION | V1 | **—** | — | — | — | — | **MISSING** (BLOCKER-resolved: unbuilt, safe) |
| CAP-FARM-REGISTRY | V1 | **—** (fail-closed stub in identity) | — | — | `FarmDirectoryService` (no impl) | — | **MISSING** (documented, fail-closed) |
| CAP-SPATIAL-STORAGEREF | V2 | inventory (seam only) | — | — | — | — | **PARTIAL** (seam exists; concrete storage location absent) |
| CAP-INVENTORY-CLASS | V2 | inventory | — | — | `InventoryService` | — | **PARTIAL** (lot/batch + category refs present in `ItemEntity`/`LotEntity`; full FEED/MEDICATION policy + expiry/storage-policy unconfirmed) |
| CAP-WAREHOUSE-LOCATION | V2 | **—** | — | — | — | — | **MISSING** |
| CAP-WAREHOUSE-QR | V2 | **—** | — | — | — | — | **MISSING** |
| CAP-WAREHOUSE-SLICE | V2 | **—** (order has task machine, no put-away/pick) | — | — | — | — | **MISSING** |
| CAP-DEVICE-REGISTRY | V3 | **—** | — | — | — | — | **MISSING** |
| CAP-MQTT-TRANSPORT | V3 | libs/messaging + per-service clients | — | — | — | full topic/QoS/persistence/dedupe | **IMPLEMENTED_BETTER than "V3 planned"** (transport is already production-grade for domain events; sensor transport unbuilt) |
| CAP-TELEMETRY-PERSIST | V3 | **—** | — | — | — | — | **MISSING** |
| CAP-RULE-ENGINE | V3 | **—** | — | — | — | — | **MISSING** |
| CAP-ACTUATOR-CONTROL | V3 | **—** | — | — | — | — | **MISSING** |
| CAP-SIMULATOR | V3 | platform/farm-simulator | stub `POST /simulations/{type}` (disabled) | — | — | log-only observer | **PARTIAL→SKELETON** (DEMO_OR_SKELETON per Phase 2) |
| CAP-SAFETY-OBS | V3 | platform/readiness (partial) | `/api/v1/platform/readiness` | — | `PlatformReadinessService` | — | **PARTIAL** (readiness probing real; sensor-plausibility/failsafe unbuilt) |
| CAP-E2E | V3 | scripts/release/* harnesses | — | — | — | — | **PARTIAL** (shell harnesses, not automated V3 E2E) |

### Reverse gaps (modules without a backlog line) — **UNMAPPED**
- `apps/smartfarm-web` — UI app, no backlog capability. → **DOCUMENT_ONLY**.
- `platform/smartfarm-readiness-service` — maps loosely to CAP-SAFETY-OBS but is its
  own thing. → **DOCUMENT_ONLY** (add to architecture map).
- `apps/smartfarm-gateway` — edge tier, correctly not a capability owner. → OK.

---

## 3. What the reconciliation says to DO (feeds Phase 5)

**Keep & document (dominant case):** the V1 business plane (identity, order=Work,
inventory, livestock, health, finance, reporting) and the event/MQTT infrastructure
are real and in several places **stronger than the backlog** (durable saga, poison
classifier, projection gap recovery, HMAC envelope). These are **KEEP** +
**DOCUMENT_ONLY/UPDATE-backlog**, never rewrite. (Per source-of-truth priority: rule
#3/#4 — current contract + better-than-backlog source wins over old backlog.)

**The real divergences, in priority order:**
1. **Naming drift** — "Work/Task" backlog owner → `order-service`. Backlog +
   `SERVICE-OWNERSHIP.md` + architecture docs must record the rename. **DOCUMENT_ONLY**
   (DOC-01).
2. **Scope gap, V2 warehouse plane** — StorageLocation / QR / put-away-pick slice are
   MISSING. This is a *roadmap* decision (defer vs build), not cleanup. **BLOCKER** for
   any attempt to mark V2 "done"; otherwise a backlog annotation. (BL-01)
3. **Scope gap, V3 automation plane** — device registry / telemetry persist / rule
   engine / actuator control MISSING; only a skeleton simulator. **BLOCKER** for V3
   "done"; the simulator is a **REPLACE-or-quarantine** candidate (SKEL-01).
4. **Documented safe stub** — FarmDirectory fail-closed fallback. Keep the fallback,
   record the gap. **DOCUMENT_ONLY** (DOC-02). Do **not** remove the fallback (removing
   it would fail *open* on farm-access checks — security regression).
5. **Hygiene PARTIALs** — inventory tests, health/reporting Flyway, reporting
   `file://` + placeholder report types. **UPDATE** items (UPD-02..05).

**No capability is OBSOLETE/REMOVE at the capability level.** The only REMOVE/REPLACE
candidates are implementation-level (the simulator skeleton), not backlog capabilities
— the supersede chain is purely additive (Phase 1 FACT), so nothing was deprecated.

**Stop-rule honoured:** V2/V3 missing planes are flagged, not "built on a guess" and
not marked done. The farm-directory stub is confirmed safe before being left in place.
