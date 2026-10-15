# REST API — SmartFarm Platform

> Reconciled 2026-10-15 against current gateway controllers/security and the identity service. Gateway ingress defaults to `:8080`; identity listens on `:8092` and binds loopback by default.

## Gateway routes

The gateway exposes auth proxy routes under `/api/v1/auth/**`, `GET /api/v1/me`, order and order-saga REST routes, and development-profile facades for livestock, inventory, health, and reporting. Exact route-to-controller mappings and authorization annotations are maintained in [`gateway-mapping.md`](../architecture/gateway-mapping.md); do not infer a route from a proto RPC.

Identity auth operations include login, register (when enabled), refresh, logout, bootstrap-superadmin, and MFA endpoints. Login accepts email/password and optional `tenantId`; identity resolves a single active tenant automatically or returns tenant-selection state when the account has multiple active memberships.

`GET /api/v1/me` is a thin identity (`subject`, `tenantId`) response, not the full profile or membership representation. The full principal/profile path is GraphQL/gRPC-backed.

## Internal service-token HTTP endpoint

Identity also has `POST /internal/service-token`. The HTTP resource-server configuration permits the route through its bearer-token filter, but the controller additionally requires the dedicated `X-Identity-Internal-Secret` header. The request body carries `clientId`, `secret`, `audience`, and `tenantId`, with optional `actorId`. `ServiceTokenService` validates the client secret, audience allowlist, scopes, and whether delegation is enabled.

The separate shared header secret adds an application authentication check; it does not encrypt the request. Identity binds to loopback by default, and the header secret must be independently provisioned through `IDENTITY_INTERNAL_API_SECRET` in both gateway and identity. Use TLS/mTLS for any network boundary and retire this HTTP path if it cannot be kept private. See [gRPC API](grpc-api.md#service-token-flow-post-internalservice-token) and [Identity service](../services/identity.md).

## Security and environments

- Gateway routes enforce JWT/scope policy as configured in gateway source; development-only service facades should not be described as production API.
- Identity's internal service-token route passes the bearer filter chain but requires the dedicated internal header and client credentials in the handler.
- gRPC TLS is disabled by default. Production identity HTTP/gRPC bind addresses default to loopback and are configurable; network exposure requires an authenticated transport boundary.
- OpenAPI exposure is controlled by the gateway configuration. Check the live profile/configuration rather than assuming `/v3/api-docs` is enabled in every deployment.

← [gRPC API](grpc-api.md) · [Events](events.md) · [Gateway mapping](../architecture/gateway-mapping.md)
