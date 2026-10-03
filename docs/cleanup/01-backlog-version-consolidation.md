# 01 — Backlog Version Consolidation (V1 → V2 → V3)

> **Phase 1 of the backlog cleanup audit. AUDIT-ONLY — no source files were edited.**
> Scope: every file under `backlog/`. Maven was **not** run.
> Each conclusion is labelled **FACT** (verified from a read file / confirmed directory),
> **INFERENCE** (reasoned from backlog + module layout), **UNKNOWN** (not determinable from
> backlog alone), **RISK** (likely to cause rework/defect), **BLOCKER** (stops a later phase).

Evidence base:
- All backlog meta files + all V1/V2/V3 story files were read (28 files).
- Actual module layout was confirmed by directory listing (not by Maven build).

---

## 1. Version overview + supersede chain

**FACT** — The roadmap is three successive versions, each a strict extension of the previous
contract, per `backlog/README.md` and `backlog/NO-REWORK-STRATEGY.md`:

| Version | Theme | Core intent (FACT, from `00-README`/`README.md`) |
|---|---|---|
| **V1 — Core Platform** | Farm → Warehouse → Inventory → Task → Worker → Livestock → Health → Finance + **IoT contract only** | Lock identity, event, coordinate, task, **device + telemetry contracts**. No real IoT, no 3D/camera. |
| **V2 — Warehouse Operations** | Precise physical `StorageLocation`, QR resolve, storage/category policy, warehouse vertical slice | **Extends spatial only** into storage location. No 3D, no camera. |
| **V3 — Connected Farm** | Real sensors (temp/humidity/gas), MQTT, rule engine, heater/fan actuation, safety/observability | **Extends device/telemetry only** into automation. |

**Supersede chain (INFERENCE from NO-REWORK-STRATEGY "V1 freeze → V2 adds → V3 adds"):**

```
V1 Device abstraction (DeviceId/Type/Capability/Telemetry/Command envelope)
      │  frozen in V1.5
      └──► V3 Device Registry + MQTT + Telemetry persistence + Rule engine + Actuator control
                 (adds real devices; does NOT redefine the V1 contract)

V1 Spatial foundation (Farm/Zone/Area/SpatialObject/Coordinate convention + generic StorageLocationRef)
      │  frozen in V1.2
      └──► V2 StorageLocation (Warehouse→Area→Rack→Level→Bin→StorageLocationId) + QR resolver
                 (adds concrete storage identity; StorageLocationRef in V1 is the extension seam)

V1 Inventory (stock ledger, receive/issue/adjust/reserve)
      └──► V2 Inventory Classification (category policy) + Warehouse vertical slice (put-away/pick)
                 (adds policy + flow; same ledger)

V1 Work (Task state machine ACCEPTED/IN_PROGRESS/COMPLETED/BLOCKED)
      └──► V2 Warehouse task vertical slice (reuses task state machine for pick/put-away)
```

**FACT** — Nothing in V2 or V3 is marked as *removing* a V1 capability. The design is purely
additive (NO-REWORK-STRATEGY §"Freeze in V1"). The only "removed" items are the out-of-core
set (§4), which were removed from the *roadmap*, not superseded between versions.

**UNKNOWN** — **Status of every item.** All story files use empty markdown checkboxes `- [ ]`.
There is **no `[x]`, no "DONE", no date, no owner-signoff anywhere in `backlog/`.** The backlog
encodes *intent*, not *completion*. Actual build state must come from later phases (code/tests),
not from this file set.

---

## 2. Consolidated capability list (latest effective version)

**INFERENCE** — Capabilities assigned stable IDs that persist across versions. "Effective
version" = the latest version that defines/extends the capability.

| Capability ID | Capability | Introduced | Effective (latest) | Notes |
|---|---|---|---|---|
| CAP-IDENTITY-CORE | Tenant/Farm/User/Actor ID standardization, UUID policy | V1.1 | V1 | Freeze item |
| CAP-IDENTITY-RBAC | RBAC / permission model | V1.1 | V1 | |
| CAP-IDENTITY-S2S-AUTH | Service-to-service authentication | V1.1 | V1 | |
| CAP-EVENT-ENVELOPE | Event envelope + schemaVersion, Correlation/Causation/EventId | V1.1 | V1 | Freeze item |
| CAP-EVENT-OUTBOX | Outbox/inbox pattern, no cross-service DB access | V1.1 | V1 | |
| CAP-IDEMPOTENCY | Command idempotency key | V1.1 | V1 | Freeze item |
| CAP-AUDIT-ACTOR | Audit actor / audit pattern | V1.1 | V1 | Freeze item |
| CAP-SPATIAL-FOUNDATION | Farm/Zone/Area/SpatialObject, coordinate+unit+axis+origin convention, versioned layout | V1.2 | V1 | Freeze; **no 3D** |
| CAP-SPATIAL-STORAGEREF | Generic `StorageLocationRef` (seam for V2) | V1.2 | V2 | Extended by CAP-WAREHOUSE-LOCATION |
| CAP-FARM-REGISTRY | Farm / Zone registry (owner-level domain) | V1.1/1.2 | V1 | Owner = "Farm" (no module — §3) |
| CAP-INVENTORY-LEDGER | Product master, UoM, immutable stock ledger, receive/issue/adjust/reserve, optimistic lock | V1.3 | V1 | |
| CAP-WORK-TASK | Task + assignment + state machine (ACCEPTED/IN_PROGRESS/COMPLETED/BLOCKED), scheduler | V1.3 | V1 | Owner = "Work" (maps to order-service — §3) |
| CAP-LIVESTOCK | Farm/Barn/Pen, Animal/Herd, lifecycle, feeding, placement | V1.4 | V1 | |
| CAP-HEALTH | Health record, treatment, vaccination, quarantine, manual observation | V1.4 | V1 | **No auto-diagnosis** |
| CAP-FINANCE-LEDGER | Expense, financial ledger, reversal, link to inventory txn | V1.4 | V1 | |
| CAP-REPORTING | Read models: daily/inventory/task/livestock summaries, reconciliation | V1.4 | V1 | |
| CAP-DEVICE-CONTRACT | Device identity/type/capability abstraction (V1 contract only) | V1.5 | V1 | Freeze; seam for V3 |
| CAP-TELEMETRY-ENVELOPE | Telemetry envelope schema (deviceId..schemaVersion) | V1.5 | V1 | Freeze |
| CAP-COMMAND-ENVELOPE | Command envelope schema (commandId..correlationId) | V1.5 | V1 | Freeze |
| CAP-WAREHOUSE-LOCATION | Warehouse→Area→Rack→Level→Bin→`StorageLocationId`, capacity, status, version | V2.1 | V2 | Extends CAP-SPATIAL-STORAGEREF |
| CAP-WAREHOUSE-QR | QR parser/validator/resolver, import-from-label, dedupe, reassignment | V2.2 | V2 | QR = resolver only, not identity |
| CAP-INVENTORY-CLASS | Product category (FEED/MEDICATION/SUPPLEMENT), storage policy, expiry, batch/lot | V2.3 | V2 | Extends CAP-INVENTORY-LEDGER |
| CAP-WAREHOUSE-SLICE | Receive→put-away→pick→issue vertical slice, worker/manager confirmation | V2.4 | V2 | Reuses CAP-WORK-TASK |
| CAP-DEVICE-REGISTRY | Real device registration: sensor/actuator/gateway types, calibration, firmware, safe state | V3.1 | V3 | Extends CAP-DEVICE-CONTRACT |
| CAP-MQTT-TRANSPORT | MQTT topics/auth/TLS/QoS/retained/dedupe/offline-buffer/reconnect | V3.2 | V3 | |
| CAP-TELEMETRY-PERSIST | Telemetry persistence, aggregation, heartbeat, quarantine | V3.2 | V3 | Extends CAP-TELEMETRY-ENVELOPE |
| CAP-RULE-ENGINE | Env rule engine: threshold/hysteresis/duration/cooldown/priority/schedule/failsafe | V3.3 | V3 | |
| CAP-ACTUATOR-CONTROL | Heater & ventilation state machines, command lifecycle, safety cutoffs | V3.4 | V3 | Extends CAP-COMMAND-ENVELOPE |
| CAP-SIMULATOR | DEV simulator (same contract as PROD), deterministic + scenario + clock-control modes | V3.5 | V3 | |
| CAP-SAFETY-OBS | Default safe state, emergency OFF, plausibility, stale detection, metrics/observability | V3.6 | V3 | |
| CAP-E2E | End-to-end scenarios (heat/humidity/gas/failure) | V3.7 | V3 | Acceptance gate |

---

## 3. Capability → effective version → expected owner → expected contract → actual-module-exists

**FACT (actual modules confirmed by directory listing):**
`libs/{smartfarm-common-kernel, smartfarm-security, smartfarm-messaging, smartfarm-proto}`;
`services/{smartfarm-identity-service, smartfarm-order-service, smartfarm-inventory-service, smartfarm-livestock-service, smartfarm-health-service, smartfarm-finance-service, smartfarm-reporting-service}`;
`apps/{smartfarm-gateway, smartfarm-web}`;
`platform/{smartfarm-readiness-service, smartfarm-farm-simulator}`.

**FACT** — There is **no** `farm-service`, `spatial-service`, `work-service`, `device-service`,
`telemetry-service`, `environment-automation`, or `actuator/control` module. The backlog
(`SERVICE-OWNERSHIP.md`) names all of these as owners.

Contract column is **INFERENCE** from the backlog (MQTT/REST explicit in V3; REST/GraphQL for
business services inferred from `apps/smartfarm-gateway` + `smartfarm-web` existing).

| Capability ID | Eff. ver | Expected owner (backlog) | Expected contract | Actual module | Exists? |
|---|---|---|---|---|---|
| CAP-IDENTITY-CORE / RBAC / S2S-AUTH | V1 | Identity | REST + S2S auth | `services/smartfarm-identity-service`, `libs/smartfarm-security` | **yes** |
| CAP-EVENT-ENVELOPE / OUTBOX / IDEMPOTENCY / AUDIT | V1 | Platform (cross-cutting) | internal event bus / outbox | `libs/smartfarm-common-kernel`, `libs/smartfarm-messaging` | **yes** |
| CAP-FARM-REGISTRY | V1 | **Farm** | REST | *(none — no farm-service)* | **no** |
| CAP-SPATIAL-FOUNDATION | V1 | **Spatial** | REST | *(none — no spatial-service)* | **no** |
| CAP-SPATIAL-STORAGEREF | V2 | **Spatial** / Inventory boundary | REST | *(none; likely folded into inventory)* | **unclear** |
| CAP-INVENTORY-LEDGER | V1 | Inventory | REST/GraphQL | `services/smartfarm-inventory-service` | **yes** |
| CAP-WORK-TASK | V1 | **Work** | REST | `services/smartfarm-order-service` (naming gap) | **yes (renamed)** |
| CAP-LIVESTOCK | V1 | Livestock | REST | `services/smartfarm-livestock-service` | **yes** |
| CAP-HEALTH | V1 | Health | REST | `services/smartfarm-health-service` | **yes** |
| CAP-FINANCE-LEDGER | V1 | Finance | REST | `services/smartfarm-finance-service` | **yes** |
| CAP-REPORTING | V1 | Reporting | REST/read-model | `services/smartfarm-reporting-service` | **yes** |
| CAP-DEVICE-CONTRACT / TELEMETRY-ENVELOPE / COMMAND-ENVELOPE | V1 | Device + Telemetry (contract) | schema/proto | `libs/smartfarm-proto` (likely) | **unclear** |
| CAP-WAREHOUSE-LOCATION | V2 | Inventory/Warehouse boundary | REST | `services/smartfarm-inventory-service` (assumed) | **unclear** |
| CAP-WAREHOUSE-QR | V2 | Inventory/Warehouse boundary | REST | `services/smartfarm-inventory-service` (assumed) | **unclear** |
| CAP-INVENTORY-CLASS | V2 | Inventory | REST | `services/smartfarm-inventory-service` | **yes (module), content unclear** |
| CAP-WAREHOUSE-SLICE | V2 | Inventory + Work | REST | `inventory-service` + `order-service` | **partial** |
| CAP-DEVICE-REGISTRY | V3 | **Device** | REST + MQTT | *(none — no device-service)* | **no** |
| CAP-MQTT-TRANSPORT | V3 | **MQTT infrastructure** | MQTT | *(none; gateway? simulator?)* | **unclear** |
| CAP-TELEMETRY-PERSIST | V3 | **Telemetry** | MQTT → store | *(none — no telemetry-service)* | **no** |
| CAP-RULE-ENGINE | V3 | **Environment Automation** | internal | *(none — no automation module)* | **no** |
| CAP-ACTUATOR-CONTROL | V3 | **Device/Control** | MQTT command | *(none — no control module)* | **no** |
| CAP-SIMULATOR | V3 | Simulator (DEV) | MQTT (same contract) | `platform/smartfarm-farm-simulator` | **yes** |
| CAP-SAFETY-OBS | V3 | Device + Automation | metrics/observability | *(partial — `platform/smartfarm-readiness-service`?)* | **unclear** |
| CAP-E2E | V3 | cross-cutting | test harness | *(test-level)* | **unclear** |

Gateway/web: `apps/smartfarm-gateway` + `apps/smartfarm-web` exist (FACT) but are not named in
`SERVICE-OWNERSHIP.md` as capability owners — they are the edge/UI, consistent with V1 "gateway".

---

## 4. Out-of-core exclusions (explicitly removed)

**FACT** — `backlog/WHAT-WAS-REMOVED.md` + `README.md §"Loại khỏi roadmap chính"` remove these
from core and forbid them from becoming core dependencies:

| Excluded capability | Reason (FACT, from file) | Allowed re-entry path |
|---|---|---|
| **3D warehouse / visualization** | Useful but not required to operate a modern farm | Later, as a *consumer* of Spatial + Warehouse APIs |
| **Camera / AI / Computer vision** | Expensive in infra, data, privacy, storage, model validation | Later, without changing Inventory/Task/Telemetry ownership |
| **YouTube streaming** | Personal content feature, not operational core | Keep outside core domain |
| **Camera evidence** | (listed with camera/AI) | Same as camera |

**FACT** — What deliberately *stays* in core: IoT environmental automation
(temperature→heater, humidity→ventilation, gas→ventilation/safety) because it has direct
operational value. Extension points for 3D/camera/AI are kept but must never be dependencies.

---

## 5. Conflicts, gaps & flags

**RISK — Owner-module naming divergence (primary cleanup target).**
`SERVICE-OWNERSHIP.md` names 7 owners that have **no matching module**: Farm, Spatial, Device,
Telemetry, Environment Automation, Actuator/Control, MQTT infrastructure. The backlog's mental
model and the actual module tree have diverged. Any traceability/ownership doc generated from
the backlog verbatim will be wrong.

**RISK — `work-service` → `order-service` rename is undocumented.**
Backlog V1.3 "Work" and `SERVICE-OWNERSHIP.md` "Work" own the Task/Worker domain. The actual
module is `smartfarm-order-service`. The rename is real (module exists) but recorded **nowhere**
in `backlog/`. A reader cannot discover it without the module tree.

**RISK — Farm & Spatial have no home.**
CAP-FARM-REGISTRY and CAP-SPATIAL-FOUNDATION are V1 *freeze* items (NO-REWORK-STRATEGY), yet no
`farm-service`/`spatial-service` exists. These are either (a) folded into another module
(**unclear which**), or (b) **MISSING**. If missing, the V1 gate item "Spatial convention is
frozen" and V2's dependence on `StorageLocationRef` sit on an unverified foundation. **BLOCKER
for Phase traceability** until it is resolved where Farm/Zone/Spatial actually live.

**RISK/BLOCKER — Entire V3 automation plane has no modules.**
Device registry, MQTT transport, telemetry persistence, rule engine, actuator control — 5 of the
V3 capabilities map to **no module**. Only `smartfarm-farm-simulator` (DEV side) and possibly
`smartfarm-readiness-service` exist. V3 as specified is largely **not represented** in the module
tree. Whether this is "not built yet" vs "built elsewhere" is **UNKNOWN** from backlog alone.

**GAP — No status anywhere.**
Every checklist is `- [ ]`. There is no done/in-progress/blocked marker, no owner, no date. The
backlog cannot answer "what is finished?" — **do not infer completion from it.**

**GAP — Acceptance criteria are checklists, not testable ACs.**
Story files list capability checkboxes + a `## Test` section of scenario names (e.g. "Concurrent
stock deduction", "Hysteresis"). These are *test intentions*, not Given/When/Then acceptance
criteria with expected results. V1.2, V2.1 have **no `## Test` block with pass/fail bars** beyond
scenario names. Converting these into verifiable ACs is itself cleanup work.

**CONFLICT — `smartfarm-messaging` vs backlog "MQTT infrastructure".**
`libs/smartfarm-messaging` exists (FACT) but the backlog's "MQTT transport" owner is a V3
infrastructure concern. Whether `smartfarm-messaging` is the internal event bus (outbox/inbox,
V1) or the MQTT layer (V3) is **unclear** and must be disambiguated — the two are different
contracts the backlog deliberately separates (NO-REWORK §"Never make MQTT client ID a domain
key").

**OBSERVATION — `apps/smartfarm-web` + `platform/smartfarm-readiness-service` are not in the
backlog at all.** The web UI and readiness service exist in the tree but no backlog capability
owns them. Reverse gap: modules without a backlog line.

---

## Appendix — Backlog file inventory (all read)

Meta: `README.md`, `IMPLEMENTATION-ORDER.md`, `SERVICE-OWNERSHIP.md`, `NO-REWORK-STRATEGY.md`,
`WHAT-WAS-REMOVED.md`.
V1: `00-README`, `01-foundation-identity-security`, `02-farm-spatial`, `03-inventory-work`,
`04-livestock-health-finance-reporting`, `05-iot-foundation`, `06-gate`.
V2: `00-README`, `01-storage-location`, `02-qr-location`, `03-inventory-classification`,
`04-warehouse-workflow`, `05-gate`.
V3: `00-README`, `01-device-registry`, `02-mqtt-telemetry`, `03-environment-rules`,
`04-actuator-control`, `05-sensor-simulator`, `06-safety-observability`, `07-e2e`.
