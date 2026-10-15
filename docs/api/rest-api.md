# REST API — SmartFarm Platform

> Reconciled 2026-10-15 against current gateway controllers/security and the identity service. Gateway ingress defaults to `:8080`; identity listens on `:8092` and binds loopback by default.

## Gateway routes

The gateway exposes auth proxy routes under `/api/v1/auth/**`, `GET /api/v1/me`, order and order-saga REST routes, and development-profile facades for livestock, inventory, health, and reporting. Exact route-to-controller mappings and authorization annotations are maintained in [`gateway-mapping.md`](../architecture/gateway-mapping.md); do not infer a route from a proto RPC.

Identity auth operations include login, register (when enabled), refresh, logout, bootstrap-superadmin, and MFA endpoints. Login accepts email/password and optional `tenantId`; identity resolves a single active tenant automatically or returns tenant-selection state when the account has multiple active memberships.

`GET /api/v1/me` is a thin identity (`subject`, `tenantId`) response, not the full profile or membership representation. The full principal/profile path is GraphQL/gRPC-backed.

Internal Identity administration and machine-token issuance use gRPC, not REST. HTTP remains for public authentication/OAuth-facing endpoints, JWKS, health, and operational probes. Gateway proxies public authentication endpoints to Identity; backend services use gRPC for synchronous service-to-service operations. See [gRPC API](grpc-api.md#service-token-issuance-and-security) and [Identity service](../services/identity.md).

## Security and environments

- Gateway routes enforce JWT/scope policy as configured in gateway source; development-only service facades should not be described as production API.
- Identity machine-token issuance validates registered client credentials and audience allowlists over gRPC; delegated exchange additionally requires a caller JWT bound to actor and tenant.
- Identity HTTP/gRPC bind addresses default to loopback and are configurable; cross-host gRPC requires TLS/mTLS and private network controls.
- OpenAPI exposure is controlled by the gateway configuration. Check the live profile/configuration rather than assuming `/v3/api-docs` is enabled in every deployment.

← [gRPC API](grpc-api.md) · [Events](events.md) · [Gateway mapping](../architecture/gateway-mapping.md)
