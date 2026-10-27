# REST API — FarmVerse Platform

> Reconciled 2026-10-15 against current gateway controllers/security and the identity service. Gateway ingress defaults to `:8080`; identity listens on `:8092` and binds loopback by default.
>
> Identity now exposes Spring Authorization Server OAuth/OIDC endpoints in addition to legacy custom auth routes. Authorization Code + PKCE/browser interoperability and downstream client migration are not release-verified yet; see the [Identity target and migration checklist](../architecture/identity-oauth2-oidc-target.md).

## Gateway routes

The gateway exposes auth proxy routes under `/api/v1/auth/**`, `GET /api/v1/me`, order and order-saga REST routes, and development-profile facades for livestock, inventory, health, and reporting. Exact route-to-controller mappings and authorization annotations are maintained in [`gateway-mapping.md`](../architecture/gateway-mapping.md); do not infer a route from a proto RPC.

Identity auth operations include login, register (when enabled), refresh, logout, bootstrap-superadmin, and MFA endpoints. Login accepts email/password and optional `tenantId`; identity resolves a single active tenant automatically or returns tenant-selection state when the account has multiple active memberships. On a fresh database, Identity generates a one-time bootstrap key and prints it in the service log; enter it in the Gateway welcome screen. The request sends the key in `X-FarmVerse-Bootstrap-Token`; only its hash is stored and it is consumed after the first super-admin is created. Setting `IDENTITY_SUPERADMIN_BOOTSTRAP_TOKEN` explicitly overrides key generation.

`GET /api/v1/me` is a thin identity (`subject`, `tenantId`) response, not the full profile or membership representation. The full principal/profile path is GraphQL/gRPC-backed.

Identity HTTP serves custom public authentication routes, OAuth `/oauth2/authorize` and `/oauth2/token`, OIDC discovery at `/.well-known/openid-configuration`, JWKS at `/oauth2/jwks`, UserInfo at `/userinfo`, plus health and operational probes. OAuth clients are provisioned from `farmverse.identity.oauth2.clients`; none are enabled by default unless deployment configuration provides them. Registered-client, authorization and consent data persist in Identity's database. Identity's legacy JWKS URL and custom auth/token APIs remain active while callers migrate. Internal administration and legacy machine-token issuance still use gRPC; Gateway still proxies custom auth routes. See [gRPC API](grpc-api.md#service-token-issuance-and-security) and [Identity service](../services/identity.md).

## Security and environments

- Gateway routes enforce JWT/scope policy as configured in gateway source; development-only service facades should not be described as production API.
- Identity machine-token issuance validates registered client credentials and audience allowlists over gRPC; delegated exchange additionally requires a caller JWT bound to actor and tenant.
- Identity HTTP/gRPC bind addresses default to loopback and are configurable; cross-host gRPC requires TLS/mTLS and private network controls.
- OpenAPI exposure is controlled by the gateway configuration. Check the live profile/configuration rather than assuming `/v3/api-docs` is enabled in every deployment.

← [gRPC API](grpc-api.md) · [Events](events.md) · [Gateway mapping](../architecture/gateway-mapping.md)
