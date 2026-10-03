# Gateway Contract Coverage (CHUNK-11)

What `apps/smartfarm-gateway` exposes at the edge, and which downstream service gRPC
contract each surface fronts. Built from source (controllers, GraphQL schema, gRPC
client configs). **FACT** unless marked otherwise. Profile column: "all" = served in
every profile; "dev" = `@Profile("dev & !prod")` (must NOT be reachable in prod).

The gateway holds no persistence; it is a stateless aggregation/edge tier that mints
per-service tokens (`ServiceTokenClient` + `BearerCallCredentials`) and translates
gRPC status → HTTP/GraphQL errors. Every mapping is `@PreAuthorize(SCOPE_...)` or
GraphQL-method authorized.

## 1. REST surface

| Path | Controller | Fronts (gRPC) | Profile |
|---|---|---|---|
| `POST /api/v1/auth/{login,refresh,…}`, `/logout` | `AuthProxyController` | identity HTTP `/internal` + credential path | all |
| `GET /whoami` | `WhoAmIController` | — (JWT echo) | all |
| `/api/v1/orders` (place/draft CRUD/get/list/cancel) | `OrderRestController` | `FarmOrderService` | all |
| `/api/v1/order-sagas/{id}` (+ retry/resume/force-*/mark-resolved) | `OrderSagaAdminController` | `OrderSagaAdministrationService` (`SCOPE_orders:saga:admin`) | all |
| `/api/v1/livestock/animals`,`/schedules` | `LivestockRegistryController` | `LivestockTaskService` | all |
| `/api/v1/livestock/tasks/*` | `LivestockDevController` | `LivestockTaskService` | **dev** |
| `/api/v1/reports/*` (create/get/list/download) | `ReportingDevController` | `ReportingService` | **dev** |
| inventory dev setup | `InventoryDevSetupController` | `InventoryService` | **dev** |

## 2. GraphQL surface (`schema.graphqls`)

| GraphQL op (type) | Resolver | Fronts (gRPC) |
|---|---|---|
| `tasks`,`task` (Query) | `FarmGraphQlController` | `LivestockTaskService` |
| `orders`,`order`,`orderSaga` (Query) | `FarmGraphQlController`/`OrderGraphQlController`/`OrderSagaGraphQlController` | `FarmOrderService`, `OrderSagaAdministrationService` |
| `dashboard` (Query, aggregate) | `FarmGraphQlController` | livestock + order + inventory + finance |
| `warehouseInventory`,`lowStock` (Query) | `FarmGraphQlController` | `InventoryService` |
| `batchCost`,`cashFlow` (Query) | `FarmGraphQlController` | `FarmFinanceService` |
| `users`,`userAuthorization`,`roles`,`permissions` (Query) | `IdentityGraphQlController` | `IdentityDirectory/Administration/PlatformAuthz` |
| `placeOrder`,`createDraftOrder`,`updateDraftOrder`,`submitDraftOrder`,`deleteDraftOrder`,`cancelOrder` (Mutation) | `OrderGraphQlController` | `FarmOrderService` |
| `retryOrderSagaStep`,`resumeOrderSaga`,`forceCompensateOrder`,`forceCancelOrder`,`forceCompleteOrder`,`markOrderSagaResolved` (Mutation) | `OrderSagaGraphQlController` | `OrderSagaAdministrationService` |
| `updateMyProfile`,`beginTotpEnrollment`,`confirmTotpEnrollment`,`disableMyMfa`,`regenerateRecoveryCodes`,`changeMyPassword`,`resetUserMfa` (Mutation) | `IdentityGraphQlController` | `IdentityCredential/Administration` |

## 3. WebSocket / event surface

| Surface | Component | Source |
|---|---|---|
| WS event stream | `EventWebSocketHandler` + `EventWebSocketConfig` | fed by `MqttEventSubscriber` → `DomainEventBus` (MQTT filter `smartfarm/+/+/domain/+/+`, envelope-verified) |

## 4. Coverage assessment

- **Covered via gateway (all profiles):** identity (auth + directory/admin/credential/MFA),
  order (+ saga admin), finance (batchCost/cashFlow + dashboard), inventory
  (warehouseInventory/lowStock + dashboard), livestock (registry).
- **Covered only via dev-profile facades:** livestock task CRUD
  (`LivestockDevController`), reporting (`ReportingDevController`), inventory dev
  setup. **RISK**: the first-class prod path for reporting and livestock-tasks is
  GraphQL / registry REST; these dev controllers must stay out of prod — confirm the
  `dev` profile is never active in a prod deploy.
- **Not fronted by the gateway:** `AnimalHealthService` (health) and
  `PlatformReadinessService` (readiness) have NO gateway edge found — reached directly
  gRPC or not yet exposed (UNKNOWN — leave to orchestrator). `FarmDirectoryService`
  has no server impl at all (see CHUNK-11 summary).
- **Health/readiness UNKNOWN**: whether an external client is expected to reach health
  or readiness through the gateway, or whether readiness is scraped out-of-band, is
  not determinable from static read.
