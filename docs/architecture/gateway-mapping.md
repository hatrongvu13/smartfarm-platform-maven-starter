# Gateway Mapping — SmartFarm Platform

> Verified from `apps/smartfarm-gateway` controllers + `graphql/schema.graphqls` + `GatewaySecurityConfiguration` + `application.yml`, @ HEAD (updated 2026-10-07).
> Gateway = Spring WebFlux reactive, :8080. REST + GraphQL (`/graphql`) edge → gRPC fan-out. Auth là ngoại lệ: HTTP-proxy sang identity qua WebClient.
> `dev-only` = controller mang `@Profile("dev & !prod")` → biến mất ở prod.

## 1. REST routes

| Method | Public path | Protocol → target | Auth | Scope/Role | Status | Controller |
|--------|-------------|-------------------|:----:|-----------|:------:|-----------|
| POST | `/api/v1/auth/register` | REST → identity | public | — | 🟢 Mapped+verified | `AuthProxyController` |
| POST | `/api/v1/auth/login` | REST → identity | public | — | 🟢 | `AuthProxyController` |
| POST | `/api/v1/auth/mfa/verify` | REST → identity | public | — | 🟢 | `AuthProxyController` |
| POST | `/api/v1/auth/refresh` | REST → identity | public | — | 🟢 | `AuthProxyController` |
| POST | `/api/v1/auth/logout` | REST → identity | public | — | 🟢 | `AuthProxyController` |
| GET | `/api/v1/me` | REST → identity | ✅ | `SCOPE_farm:read` | 🟢 | `WhoAmIController` (THIN: `{subject,tenantId}`; profile qua GraphQL `me`) |
| POST | `/api/v1/orders` | gRPC → order `PlaceOrder` | ✅ | `SCOPE_orders:write` | 🟢 | `OrderRestController` |
| POST | `/api/v1/orders/drafts` | gRPC → order `CreateDraftOrder` | ✅ | `orders:write` | 🟢 | `OrderRestController` |
| PUT | `/api/v1/orders/drafts/{id}` | gRPC → order `UpdateDraftOrder` | ✅ | `orders:write` | 🟢 | `OrderRestController` |
| POST | `/api/v1/orders/drafts/{id}/submit` | gRPC → order `SubmitDraftOrder` | ✅ | `orders:write` | 🟢 | `OrderRestController` |
| DELETE | `/api/v1/orders/drafts/{id}` | gRPC → order `DeleteDraftOrder` | ✅ | `orders:write` | 🟢 | `OrderRestController` |
| GET | `/api/v1/orders/{id}` | gRPC → order `GetOrder` | ✅ | `orders:read` | 🟢 | `OrderRestController` |
| GET | `/api/v1/orders` | gRPC → order `ListOrders` | ✅ | `orders:read` | 🟢 | `OrderRestController` |
| POST | `/api/v1/orders/{id}/cancel` | gRPC → order `CancelOrder` | ✅ | `orders:write` | 🟢 | `OrderRestController` |
| GET | `/api/v1/order-sagas/{id}` | gRPC → order saga inspect | ✅ | `orders:saga:admin` | 🟢 | `OrderSagaAdminController` |
| POST | `/api/v1/order-sagas/{id}/steps/{step}/retry` | gRPC → order saga | ✅ | `orders:saga:admin` | 🟢 | `OrderSagaAdminController` |
| POST | `/api/v1/order-sagas/{id}/resume` | gRPC → order saga | ✅ | `orders:saga:admin` | 🟢 | `OrderSagaAdminController` |
| POST | `/api/v1/order-sagas/{id}/force-compensate` | gRPC → order saga | ✅ | `orders:saga:admin` | 🟢 | `OrderSagaAdminController` |
| POST | `/api/v1/order-sagas/{id}/force-cancel` | gRPC → order saga | ✅ | `orders:saga:admin` | 🟢 | `OrderSagaAdminController` |
| POST | `/api/v1/order-sagas/{id}/force-complete` | gRPC → order saga | ✅ | `orders:saga:admin` | 🟢 | `OrderSagaAdminController` |
| POST | `/api/v1/order-sagas/{id}/mark-resolved` | gRPC → order saga | ✅ | `orders:saga:admin` | 🟢 | `OrderSagaAdminController` |
| POST | `/api/v1/livestock/animals` | gRPC → livestock | ✅ | authenticated | 🟡 dev-only | `LivestockRegistryController` |
| GET | `/api/v1/livestock/animals/{id}` | gRPC → livestock | ✅ | authenticated | 🟡 dev-only | `LivestockRegistryController` |
| GET | `/api/v1/livestock/animals` | gRPC → livestock | ✅ | authenticated | 🟡 dev-only | `LivestockRegistryController` |
| POST | `/api/v1/livestock/schedules` | gRPC → livestock | ✅ | authenticated | 🟡 dev-only | `LivestockRegistryController` |
| GET | `/api/v1/livestock/schedules` | gRPC → livestock | ✅ | authenticated | 🟡 dev-only | `LivestockRegistryController` |
| POST | `/api/v1/livestock/tasks` + `/{id}/assign·accept·complete·cancel`, GET `/`,`/{id}` | gRPC → livestock | ✅ | authenticated | 🟡 dev-only | `LivestockDevController` |
| POST | `/api/v1/inventory/items`, `/receipts` | gRPC → inventory | ✅ | authenticated | 🟡 dev-only | `InventoryDevSetupController` |
| POST | `/api/v1/reports` + GET `/{id}`,`/`,`/{id}/download` | gRPC → reporting | ✅ | authenticated | 🟡 dev-only | `ReportingDevController` |
| GET | `/api/v1/health/{observations,vaccinations,alerts}` | gRPC → health `AnimalHealthService` | ✅ | `SCOPE_health:read` | 🟡 dev-only (GW-01 fixed) | `HealthDevController` |

Public (permitAll) từ `GatewaySecurityConfiguration`: `/actuator/health*`, `/v3/api-docs/**`, swagger, `/ws/**`, 5 auth endpoint. Mọi path khác = `authenticated()`.

## 2. GraphQL (`POST /graphql`) — 19 query + 19 mutation

| Field | Type | → target | Prod-safe? | Status |
|-------|------|---------|:----------:|:------:|
| `me`, `users`, `userAuthorization`, `roles`, `permissions`, `mySecurityProfile` | Query | identity | ✅ | 🟢 |
| `platformStatus` | Query | gateway/identity | ✅ | 🟢 |
| `order`, `orders` | Query | order (v1) | ✅ | 🟢 |
| `orderV2`, `ordersV2`, `orderSaga` | Query | order | ✅ | 🟢 |
| `tasks`, `task` | Query | livestock | 🟡 dev | 🟡 |
| `dashboard`, `warehouseInventory`, `lowStock` | Query | inventory/aggregate | 🟡 dev | 🟡 |
| `batchCost`, `cashFlow` | Query | finance | 🟡 dev | 🟡 |
| `placeOrder`,`createDraftOrder`,`updateDraftOrder`,`submitDraftOrder`,`deleteDraftOrder`,`cancelOrder` | Mutation | order | ✅ | 🟢 |
| `retryOrderSagaStep`,`resumeOrderSaga`,`forceCompensateOrder`,`forceCancelOrder`,`forceCompleteOrder`,`markOrderSagaResolved` | Mutation | order saga (admin) | ✅ | 🟢 |
| `updateMyProfile`,`beginTotpEnrollment`,`confirmTotpEnrollment`,`disableMyMfa`,`regenerateRecoveryCodes`,`changeMyPassword`,`resetUserMfa` | Mutation | identity | ✅ | 🟢 |

## 3. Phân loại

1. **Mapped & verified**: toàn bộ auth, `/api/v1/me` (THIN, scope `farm:read`), orders REST, saga admin REST, order+identity GraphQL.
2. **Mapped but incomplete (dev-only, chưa có đường prod)**: livestock/inventory/reporting REST + `tasks/dashboard/warehouseInventory/batchCost/cashFlow/lowStock` GraphQL. Javadoc gọi "DEV REST facade" → prod equivalent **có vẻ dự định nhưng chưa build**.
3. **Implemented in service but NOT exposed by gateway**: xem §4.
4. **Documented but not implemented**: không phát hiện route tài liệu-hóa mà không có impl (sau khi sửa README broken link).
5. **Unknown / NEEDS_VERIFICATION**: hành vi prod thực tế của các facade dev-only.
6. **Deprecated**: duplicate order read surface (`order`/`orders` v1 vs `orderV2`/`ordersV2`) — ứng viên hợp nhất, chưa deprecate chính thức.

## 4. Endpoint có trong service nhưng CHƯA mapping qua gateway

- 🟡 **health-service — dev REST facade (GW-01 fixed)**: gateway khai báo gRPC client `AnimalHealthService` (`HealthDevClientConfig`, :9097) + `HealthDevController` (`/api/v1/health/{observations,vaccinations,alerts}`, dev-only, scope `health:read`). 6/10 RPC còn chưa impl ở service; prod route chưa có.
- 🟡 **finance-service**: không REST, chỉ 2 GraphQL dev-only (`batchCost`,`cashFlow`). 6 RPC finance server không có caller khác saga.
- 🟡 **identity**: REST `/api/v1/auth/mfa/enrollment/begin|confirm` **không** được `AuthProxyController` proxy (chỉ có qua GraphQL mutation `beginTotpEnrollment/confirmTotpEnrollment`). Saga op `recover-stale` không expose.
- Nhiều RPC admin identity/platform-auth/directory có impl nhưng không caller gateway (xem [grpc trace](#) trong `unresolved-items`).

## 5. Gateway route trỏ endpoint không tồn tại
Không phát hiện. Mọi route gRPC đều có server impl tương ứng.

## 6. Route thiếu auth/authz cần thiết
Không phát hiện lỗ hổng: ngoài 5 auth public + health + docs + ws, mọi path `authenticated()`; order/saga có `@PreAuthorize` scope cụ thể. (Lưu ý ISSUE-12: super-admin `SCOPE_*` bypass — authz, không phải thiếu auth.)

## 7. Route nguy cơ vòng lặp / xung đột path
- Không phát hiện vòng lặp (order→inventory/finance là cây saga 1 chiều, có compensation).
- **Duplicate/overlap read surface**: `order`/`orders` vs `orderV2`/`ordersV2`; hai bề mặt `me` khác nhau — REST `/api/v1/me` là THIN (`{subject,tenantId}`, scope `farm:read`), GraphQL `me` trả về Principal đầy đủ (profile+membership). Không xung đột path, phân vai rõ: scopes lấy từ JWT, profile lấy từ GraphQL `me`.

← [System Overview](./system-overview.md) · [Documentation Index](../index.md)
