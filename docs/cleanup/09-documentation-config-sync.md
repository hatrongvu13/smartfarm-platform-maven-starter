# 09 — Documentation / Configuration Sync (Phase 8)

> Records the doc/config divergences and what was reconciled. Source is the truth;
> backlog/docs were brought into line with it (never the reverse).

## Documentation

| ID | Item | Status |
|---|---|---|
| DOC-01 | "Work/Task" owner → `smartfarm-order-service` rename | **DONE** — `backlog/SERVICE-OWNERSHIP.md` now shows the actual module + marks the rename. |
| DOC-02 | FarmDirectory is an intentional fail-closed stub | **DONE** — recorded in `SERVICE-OWNERSHIP.md` (Farm row) and `03-backlog-implementation-traceability.md` §1/§3; `05-identity-security-conformance.md` KEEP list. |
| DOC-03 | Remove stale `OrderSagaOrchestrator` from current-architecture docs | **VERIFIED — no change needed.** `docs/architecture/order-saga.md` already describes the persistent-worker saga and refers to the orchestrator only as removed. The remaining mentions live in `docs/audit/*` + `docs/remediation/*` which are historical records of the ISSUE-03 work and must not be rewritten. |
| DOC-04 | `apps/smartfarm-web` + `readiness-service` reverse gaps | **DONE** — noted in `SERVICE-OWNERSHIP.md` edge/UI footnote and `03-...traceability.md` reverse-gaps section. README already lists all 13 modules. |
| DOC-05 | Backlog V2/V3 NOT_IMPLEMENTED annotations | **DONE** — the `SERVICE-OWNERSHIP.md` status column marks every missing V2 (warehouse) and V3 (automation) capability NOT_IMPLEMENTED. |

- Root README: already reflects the real module tree (13 modules, readiness, simulator)
  — no edit required. (FACT)
- Architecture context map: `docs/architecture/order-saga.md` matches live source
  (idempotency matrix, state machines, sequences) — current. (FACT)
- API/gRPC/MQTT/GraphQL docs: the gateway contract coverage (`04-...`) is the authoritative
  surface map produced this engagement; no stale endpoint doc was found pointing at a
  removed route (nothing was removed).

## Configuration

| Item | Status |
|---|---|
| finance prod `ddl-auto: create-drop` → `validate` (UPD-01) | **DONE** |
| health: Flyway baseline + dev/prod `ddl-auto: validate` (UPD-03) | **DONE** |
| reporting: Flyway baseline + dev/prod `ddl-auto: validate` (UPD-04) | **DONE** |
| compose postgres healthcheck user mismatch (RISK-CFG-01) | **DONE** — `pg_isready -U postgres -d smartfarm`. |
| reporting download seam → object storage (UPD-05) | **DONE** — `DownloadLocationResolver` seam; prod swaps the bean, API contract unchanged. |
| reporting placeholder report types made explicit (UPD-06) | **DONE** — logged `placeholderSummary`, documented set. |
| dev secrets in `application-dev.yml` | **DOCUMENTED** — dev placeholders, env-overridable in prod; not printed in startup logs. Left as dev defaults. |

- No unbound `@ConfigurationProperties` key was introduced; the new reporting resolver
  reuses existing `smartfarm.reporting.*` settings. Typed-property reconciliation for MQTT
  is in `10-mqtt-production-readiness.md`.
- No secret is printed in docs or startup logs (verified in CHUNK-13 + this pass).
