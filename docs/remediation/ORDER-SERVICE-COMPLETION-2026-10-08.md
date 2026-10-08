# Order Service completion pass — 2026-10-08

## Scope

Focused completion against `docs/PROJECT-STATUS.md`: preserve the persistent Saga architecture,
close verified Order gaps, add contract evidence, and reconcile active documentation.

## Applied

- Tenant-scoped operator stale-claim recovery. The scheduled worker retains the global maintenance
  operation, while `/internal/order-sagas/recover-stale` can recover only the requested tenant.
- Saga graph contract test pins reserve → finance → commit → reverse-finance → reverse release order
  and all idempotency keys, including `orderId:finance`.
- Executor contract test prevents `@Transactional` from being added around remote gRPC calls.
- Saga property test pins retry, claim, processing, manual-review and compensation defaults.
- Bruno requests added for inspect, retry-step, resume and mark-resolved administration paths.
- Active status/runtime/security/order documentation reconciled with resolved CFG-01, EVT-01/02,
  GW-01 and live-verified MQTT HMAC.

## Intentionally deferred

- ISSUE-10 publisher durable session: apply only together with broker authentication/ACL/TLS and a
  unique per-instance client identity.
- ISSUE-13 circuit breaker: requires platform-wide resilience policy. Inventory/Finance Saga
  deadlines are already externalized.
- Generic Saga runtime extraction: no second Saga owner exists.
- New proto or database migration: not required by this pass.

## Verification

```bash
mvn -pl services/smartfarm-order-service -am test
python3 apply-order-service-completion.py . --check
```

Run Bruno Saga administration requests only against a disposable/test tenant and only after setting
`sagaId` and `sagaStepKey`. Force actions remain deliberately absent from the default sequential run.
