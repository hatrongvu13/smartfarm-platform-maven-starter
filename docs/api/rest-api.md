# REST API — SmartFarm Platform

> Verified từ gateway controllers @ HEAD `eaaa112`. Tất cả qua gateway :8080. Chi tiết route + auth: [gateway-mapping.md](../architecture/gateway-mapping.md).

## Public (permitAll)
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login` → `AuthenticationResponse` (`authenticationStatus`: `COMPLETED | MFA_REQUIRED | MFA_ENROLLMENT_REQUIRED | TENANT_SELECTION_REQUIRED`)
- `POST /api/v1/auth/bootstrap-superadmin` (409 nếu đã có super-admin)
- `POST /api/v1/auth/mfa/verify`
- `POST /api/v1/auth/mfa/enrollment/begin`, `POST /api/v1/auth/mfa/enrollment/confirm`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/platform/deployment-state` → `{initialized, superAdminExists, bootstrapRequired}`
- `GET /actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`
- `/v3/api-docs/**`, swagger, `/ws/**`

## Authenticated (Bearer JWT)
- `GET /api/v1/me` — scope `farm:read` (`SCOPE_farm:read`). **THIN**: trả về `{subject, tenantId}` ONLY. Đây **KHÔNG** phải hồ sơ; lấy profile/membership qua GraphQL `me` (xem [gRPC/GraphQL](grpc-api.md)).
- `POST /api/v1/orders` (+ `/drafts`, `/drafts/{id}` PUT, `/drafts/{id}` DELETE `?expectedVersion=<long>` qua query, `/drafts/{id}/submit`, `/{id}` GET, `/` GET, `/{id}/cancel`) — scope `orders:read`/`orders:write`
- `GET|POST /api/v1/order-sagas/{id}[/...]` — scope `orders:saga:admin`

## Service-to-service (machine-to-machine, no end-user Bearer)
- `POST /internal/service-token` (identity, served at identity :8092; reached by the gateway's
  `ServiceTokenClient`) — mints a short-lived per-audience service JWT. Auth is the **client secret
  in the body** `{clientId, secret, audience, tenantId, actorId}`, so it is `permitAll` in the
  resource-server chain (a Bearer token is NOT required/used). Returns `200` with
  `{accessToken, tokenType, expiresInSeconds, audience}`; `401` on bad secret / disallowed audience
  (does not leak which); `400` on malformed body. Full flow + the former missing-handler 401 root
  cause: see [grpc-api.md → Service-token flow](grpc-api.md#service-token-flow-post-internalservice-token).

## Dev-only (`@Profile dev&!prod`)
- `/api/v1/livestock/animals*`, `/schedules*` — scope `tasks:write` (write) / `farm:read` (read); `/api/v1/livestock/tasks*`
- `/api/v1/inventory/items`, `/receipts` — scope `inventory:write`. **Không có route LIST/read REST**: tồn kho đọc qua GraphQL `warehouseInventory(itemId,warehouseId)` / `lowStock(farmId,limit)`.
- `/api/v1/reports` + `/{id}`, `/`, `/{id}/download` — scope `report:write` (tạo) / `report:read` (đọc) — **số ít** `report`, KHÔNG phải `reports`.
- `/api/v1/health/{observations,vaccinations,alerts}` — scope `health:read` (GW-01).

## Headers
- `Authorization: Bearer <access>` — mọi route authenticated.
- `Idempotency-Key` — **bắt buộc** cho các write: `POST /api/v1/orders`, `/orders/drafts`, `/inventory/items`, `/inventory/receipts`, `/reports`, `POST /livestock/tasks`.
- `X-Correlation-Id` — optional, echo trong response.

## OpenAPI / Swagger
Gateway expose `/v3/api-docs/**` + swagger (`OpenApiConfig`). GraphiQL `/graphiql` default **off**.

← [gRPC API](grpc-api.md) · [Events](events.md)
