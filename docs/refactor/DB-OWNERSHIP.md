# Database Ownership (refactor §7)

> Every persistent table, its owning bounded context, its writer, and who reads it. Grounded
> in the Flyway baselines + the gRPC/MQTT contract map (`docs/cleanup/04`, CHUNK-11/12).
> **Finding: ownership is clean — no table is written by more than one module.** Cross-module
> data flows via gRPC calls and MQTT events, never shared tables or cross-domain repositories.

## Ownership table

| Table | Owner (module) | Writer | Readers | Notes |
|---|---|---|---|---|
| `sf_tenant`, `sf_user_account`, `sf_user_profile`, `sf_tenant_membership`, `sf_permission`, `sf_role`, `sf_role_permission`, `sf_membership_role`, `sf_membership_farm`, `sf_refresh`, `sf_user_authenticator`, `sf_recovery_code`, `sf_mfa_challenge`, `sf_identity_mqtt_inbox`, `sf_identity_outbox` | identity | identity only | others reach identity via gRPC (auth/directory) — never its tables |
| `ord_order`, `ord_order_line`, `ord_order_view`, `ord_outbox`, `ord_saga`, `ord_saga_step`, `ord_projection_inbox`, `ord_projection_gap`, `ord_projection_recovery_audit`, `ord_saga_recovery_audit`, `ord_order_event_archive` | order | order only | gateway reads orders via order gRPC |
| `inv_item`, `inv_lot`, `inv_balance`, `inv_movement`, `inv_reservation`, `inv_inbox` | inventory | inventory only | order calls inventory gRPC (reserve/commit/release) — no direct table access |
| `sf_animal`, `sf_task`, `sf_task_schedule`, `sf_outbox` (livestock) | livestock | livestock only | reporting pulls livestock data via gRPC |
| `h_observation`, `h_vaccination`, `h_outbox` | health | health only | — |
| `fin_transaction`, `fin_debt` | finance | finance only | order calls finance gRPC (record/reverse expense) — no direct table access |
| `rpt_export_job` | reporting | reporting only | — |

## Cross-module analysis (§7 checks)

- **Multiple modules owning the same table:** NONE. Each table prefix (`sf_` identity &
  livestock use different names, `ord_`, `inv_`, `h_`, `fin_`, `rpt_`) belongs to exactly one
  module. (FACT — from the per-service Flyway baselines.)
- **Entities copied between modules:** NONE found. Cross-service payloads travel as protobuf
  (`libs/smartfarm-proto`) over gRPC/MQTT, not as shared JPA entities.
- **Repositories accessing another domain's tables:** NONE. Each service's repositories bind
  only to its own `@Entity` set.
- **Cross-module JPA relationships:** NONE. `@ManyToOne`/`@OneToOne` joins stay within a module.
- **Shared status enums that should be DTOs/events:** the order saga and participants
  (inventory/finance) coordinate via gRPC request/response + idempotency keys, not a shared
  status table — correct bounded-context separation.

## Note on `sf_` prefix collision
Both identity and livestock use the `sf_` prefix (`sf_user_account` vs `sf_animal`/`sf_task`).
The table NAMES do not collide (different nouns) and the services own separate databases/schemas
in deployment, so this is cosmetic, not a real ownership conflict. **INTENTIONALLY_KEEP** —
renaming would require new migrations for two services with no functional benefit.

## Verdict
The modular-monolith boundary is **clean and preserved**. No split into physical microservices
is warranted (§7) — the gRPC + MQTT + outbox/inbox separation already enforces ownership.
