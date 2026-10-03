# Service Ownership — Practical V1/V2/V3

> **Actual module + build status columns added during the cleanup audit
> (`docs/cleanup/`).** The backlog "Owner" names the intended domain owner; "Actual
> module" is the real Maven module (or — when none exists). Status: IMPLEMENTED /
> PARTIAL / NOT_IMPLEMENTED, from `docs/cleanup/03-backlog-implementation-traceability.md`.

| Capability | Owner (backlog) | Actual module | Status |
|---|---|---|---|
| Identity / Permission | Identity | `services/smartfarm-identity-service` + `libs/smartfarm-security` | IMPLEMENTED |
| Farm / Zone | Farm | — (identity ships a fail-closed FarmDirectory fallback) | NOT_IMPLEMENTED |
| Spatial / Coordinate | Spatial | — | NOT_IMPLEMENTED |
| Inventory / Stock | Inventory | `services/smartfarm-inventory-service` | IMPLEMENTED |
| Warehouse / StorageLocation | Inventory/Warehouse boundary | — (seam only; no StorageLocation domain) | NOT_IMPLEMENTED (V2) |
| Task / Worker | Work | **`services/smartfarm-order-service`** (renamed from "work-service") | IMPLEMENTED |
| Livestock | Livestock | `services/smartfarm-livestock-service` | IMPLEMENTED |
| Health | Health | `services/smartfarm-health-service` | IMPLEMENTED |
| Finance | Finance | `services/smartfarm-finance-service` | IMPLEMENTED |
| Reporting | Reporting | `services/smartfarm-reporting-service` | PARTIAL (livestock report real; others placeholder) |
| Device identity | Device | — | NOT_IMPLEMENTED (V3) |
| Telemetry | Telemetry | — | NOT_IMPLEMENTED (V3) |
| Automation rules | Environment Automation | — | NOT_IMPLEMENTED (V3) |
| Actuator command | Device/Control | — | NOT_IMPLEMENTED (V3) |
| MQTT transport | MQTT infrastructure | `libs/smartfarm-messaging` + per-service clients | IMPLEMENTED (domain-event transport; sensor transport NOT_IMPLEMENTED) |

> Edge/UI (not capability owners): `apps/smartfarm-gateway` (GraphQL/REST/WS edge),
> `apps/smartfarm-web` (UI), `platform/smartfarm-readiness-service` (platform readiness),
> `platform/smartfarm-farm-simulator` (DEV-only, quarantined — cleanup SKEL-01).

## Rules

- Spatial does not own stock.
- Inventory does not own sensor measurements.
- Telemetry does not diagnose health.
- Rule engine does not own physical device identity.
- Device service does not decide business policy.
- Worker task does not directly control hardware.
