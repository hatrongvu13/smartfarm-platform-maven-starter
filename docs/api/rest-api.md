# REST API — SmartFarm Platform

> Verified từ gateway controllers @ HEAD `eaaa112`. Tất cả qua gateway :8080. Chi tiết route + auth: [gateway-mapping.md](../architecture/gateway-mapping.md).

## Public (permitAll)
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/mfa/verify`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`
- `/v3/api-docs/**`, swagger, `/ws/**`

## Authenticated (Bearer JWT)
- `GET /me`
- `POST /api/v1/orders` (+ `/drafts`, `/drafts/{id}` PUT/DELETE, `/drafts/{id}/submit`, `/{id}` GET, `/` GET, `/{id}/cancel`) — scope `orders:read`/`orders:write`
- `GET|POST /api/v1/order-sagas/{id}[/...]` — scope `orders:saga:admin`

## Dev-only (`@Profile dev&!prod`)
- `/api/v1/livestock/animals*`, `/schedules*`, `/api/v1/livestock/tasks*`
- `/api/v1/inventory/items`, `/receipts`
- `/api/v1/reports` + `/{id}`, `/`, `/{id}/download`

## Headers
- `Authorization: Bearer <access>` — mọi route authenticated.
- `Idempotency-Key` — bắt buộc cho `POST /api/v1/orders`, `/drafts`.
- `X-Correlation-Id` — optional, echo trong response.

## OpenAPI / Swagger
Gateway expose `/v3/api-docs/**` + swagger (`OpenApiConfig`). GraphiQL `/graphiql` default **off**.

← [gRPC API](grpc-api.md) · [Events](events.md)
