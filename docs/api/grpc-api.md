# gRPC API — SmartFarm Platform

> Reconciled 2026-10-15 against `libs/smartfarm-proto/src/main/proto` and server implementations. There are 14 declared gRPC services and 127 RPC methods; 116 have handlers, while 11 remain unimplemented (5 `FarmDirectoryService`, 6 `AnimalHealthService`). A proto method or Java bean alone does not prove runtime registration, caller coverage, or production security.
>
> The machine/delegated token RPC details below describe the current custom implementation. The adopted target replaces token grants with OAuth endpoints; directory and administration gRPC remain in scope.

## Service traceability

| Proto service | Implementation/caller status |
|---|---|
| `IdentityDirectoryService` | Implemented; Gateway GraphQL uses principal/profile and permission operations. |
| `IdentityAdministrationService` | Implemented; Gateway GraphQL uses user, membership, role and tenant-role assignment operations. Tenant permission catalog CRUD is deprecated and rejected; permission assignments to tenant roles remain supported. |
| `PlatformAuthorizationAdministrationService` | Implemented; Gateway routes global permission catalog mutations here. Platform role caller coverage is incomplete. |
| `IdentityCredentialService` | Implemented; Gateway GraphQL exposes profile/MFA credential operations. |
| `IdentityTokenService` | Five handlers implemented: machine issue, delegated exchange, and three session operations. Gateway/Order/Reporting now use gRPC issuance clients; focused RPC/client tests exist, but server-level registration and multi-service runtime coverage remain open. |
| `FarmOrderService`, `OrderSagaAdministrationService` | Implemented; Gateway REST/GraphQL and Order's saga integrations use gRPC. |
| `InventoryService` | Implemented, including `AdjustStock` and `TraceLot`; callers include Order and development Gateway routes. |
| `FarmFinanceService` | Implemented; Order saga uses finance RPCs. |
| `LivestockTaskService` | Implemented; Gateway development facades and Reporting use it. |
| `AnimalHealthService` | Four RPCs implemented and exposed by a development Gateway facade; six RPC handlers are missing. |
| `ReportingService` | Implemented; Gateway development facade is a caller. |
| `PlatformReadinessService` | Implemented; readiness aggregator calls downstream health/readiness. |
| `FarmDirectoryService` | All five RPCs unimplemented (planned). |
| `common`, `events`, `telemetry` | Shared/message-only contracts; no independent service implementation. |

## Port map

Ports are defaults; inspect the active profile and environment overrides before deployment.

| Service | gRPC | HTTP reference |
|---|---:|---:|
| Gateway | — | 8080 |
| Identity | 9092 | 8092 |
| Livestock | 9091 | 8081 |
| Inventory | 9093 | 8083 |
| Finance | 9094 | 8084 |
| Order | 9095 | 8085 |
| Reporting | 9096 | 8086 |
| Health | 9097 | 8087 |

## Service-token issuance and security

Synchronous internal operations and legacy custom service-token issuance currently use gRPC. Public custom authentication routes, OAuth/OIDC protocol endpoints, JWKS, health, and operations endpoints use HTTP. OAuth client credentials are available for configured clients, but existing service clients remain on gRPC; RFC 8693 exchange and caller migration are not yet implemented.

- **`IdentityTokenService.IssueServiceToken`** is used by machine clients such as Order and Reporting. Identity validates registered client credentials, the audience allowlist, and configured scopes before minting a short-lived service JWT. This is the bootstrap token path and does not require an existing bearer JWT. It must stay on the private gRPC listener; Identity binds to loopback by default.
- **`IdentityTokenService.ExchangeDelegatedToken`** is used by Gateway for user-originated calls. It requires a verified user JWT in gRPC metadata; Identity requires the requested actor and tenant to match the JWT and then revalidates account, membership, client delegation, audience, and authorization versions.
- Callers cache the returned token and attach it to downstream calls with `BearerCallCredentials`. Secrets and token payloads must not be logged.
- Newly issued custom Identity JWTs carry `identity_claims_version: 1`, defined by the shared `TokenClaimContract`; resource servers continue accepting legacy tokens without the version claim and reject versions they do not support. Async/exp-less tokens are no longer issued, and validators require finite expiry. OAuth tokens are separately audience-bound; migrate and validate downstream claim handling before retiring the custom token contract.
- **Session RPCs** require their specific session authorities and do not return raw refresh-token material.
- TLS/mTLS is required before cross-host deployment; loopback binding is the current single-host boundary, not a substitute for transport security on a network.
- Focused unit tests cover token RPC actor binding, policy, and the Gateway client. Dedicated server registration/enforcement and live multi-service tests remain release gates.

For new gRPC resource services, `smartfarm-security` auto-registers the inbound JWT interceptor but does not infer per-method scopes, provide an OAuth token client, or enforce database tenant isolation. See the [service integration guide](../security/identity-service-integration.md), [Identity service](../services/identity.md), and [Security architecture](../security/security-architecture.md).

## Identity gRPC error details

Identity maps domain failures to a gRPC status and attaches the stable machine-readable code in the `identity-error-code` trailing metadata field. Business exceptions preserve their domain code; generic failures use the corresponding gRPC status name, and unexpected internal errors expose only `INTERNAL`. Gateway REST and GraphQL gRPC facades preserve this trailer as the client-facing error code, falling back to the shared gRPC-status mapping when the trailer is absent. Clients should branch on the code/status rather than parse the human-readable status description.

## Standardized error codes (gRPC → HTTP)

`GrpcStatusHttpMapping` translates downstream status codes for Gateway REST and GraphQL facades.

| gRPC status | HTTP | Stable code |
|---|---:|---|
| `INVALID_ARGUMENT`, `OUT_OF_RANGE` | 400 | `BAD_REQUEST` |
| `UNAUTHENTICATED` | 401 | `UNAUTHENTICATED` |
| `PERMISSION_DENIED` | 403 | `FORBIDDEN` |
| `NOT_FOUND` | 404 | `NOT_FOUND` |
| `ALREADY_EXISTS` | 409 | `CONFLICT` |
| `FAILED_PRECONDITION` | 409 | `FAILED_PRECONDITION` |
| `ABORTED` | 409 | `VERSION_CONFLICT` |
| `RESOURCE_EXHAUSTED` | 429 | `RATE_LIMITED` |
| `DEADLINE_EXCEEDED` | 504 | `TIMEOUT` |
| `UNAVAILABLE`, `UNIMPLEMENTED` | 502 | `DOWNSTREAM_UNAVAILABLE` |
| Other or missing status | 500 | `INTERNAL` |

← [System Overview](../architecture/system-overview.md) · [Events](events.md) · [REST API](rest-api.md)
