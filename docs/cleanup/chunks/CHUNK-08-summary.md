# CHUNK-08 — services/smartfarm-finance-service

**Scope:** farm finance ledger (income/expense), debts (payable/receivable),
batch-cost + cash-flow aggregates.

**Graph manifest (graphslice --summary):**
- 89 nodes · inbound 0 / outbound 125
- annotations: {Entity:6, Service:5, Repository:4, GrpcService:3, Configuration:2}

**5-line summary**
1. Entry points: 1 gRPC service `FarmFinanceGrpcService` (10 RPCs). No REST, no MQTT.
2. Persistence: Flyway `V1__finance_baseline` (`fin_transaction`, `fin_debt`); money as integer minor units + ISO-4217; JPQL aggregates for batch cost and cash flow.
3. Idempotency/auth: every write keyed on (tenant_id, idempotency_key) with DB unique + compare guard; `reverseExpense` is the saga compensation; tenant from verified gRPC context; per-method `SCOPE_finance:write/read`.
4. Tests: 0 under src/test.
5. Verdict: PRODUCTION_IMPLEMENTATION of logic — **BUT BLOCKER (FACT): `application-prod.yml` ddl-auto=create-drop drops+recreates the finance schema on every prod restart (data loss)**; also no unit tests. Fix prod ddl-auto to `validate`.
