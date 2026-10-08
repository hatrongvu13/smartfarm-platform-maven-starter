# Order Detail completion — 2026-10-08

## Implemented

- Added `GetOrderSagaByOrder` to the shared Order proto contract.
- Added tenant-isolated Order → Saga lookup in Order Service and protected it with
  `SCOPE_orders:saga:admin`.
- Added REST `/api/v1/order-sagas/by-order/{orderId}` and GraphQL
  `orderSagaByOrder(orderId: ID!)` through Gateway.
- Expanded Web Order Detail with business overview, formatted totals, lifecycle stage,
  privileged Saga inspection, step errors, retry, resume and manual-resolution actions.
- Added Bruno lookup-by-order which automatically stores `sagaId` and `sagaStepKey`.
- Added a proto descriptor contract test.

## Security boundary

Users with `orders:read` see business Order fields and a derived processing stage. Internal
Saga identifiers, downstream external references and error detail require `orders:saga:admin`.
Force compensate/cancel/complete remain available through privileged APIs but are intentionally
not exposed as default Web controls.

## Verification

```bash
mvn -pl services/smartfarm-order-service,apps/smartfarm-gateway -am test
npm --prefix apps/smartfarm-web run typecheck
npm --prefix apps/smartfarm-web run build
git diff --check
```
