# Identity Service (`services/smartfarm-identity-service`)

> Reconciled 2026-10-15 against the service source, its tests, its runtime configuration, and `libs/smartfarm-proto/src/main/proto/smartfarm/identity/v1/identity.proto`. The implementation is authoritative; this page is not a release-readiness claim.

## Responsibility and runtime

Identity owns account/profile, tenant membership, RBAC and permission administration, MFA, JWT issuance/JWKS, service-client validation, and refresh-session lifecycle. HTTP defaults to `127.0.0.1:8092`; gRPC port `9092` is configured in base configuration and binds to loopback by default in every profile (`IDENTITY_GRPC_BIND_ADDRESS`).

The service has five gRPC service implementations: directory, administration, platform authorization administration, credentials, and token/session. Each is registered as a Spring gRPC `@GrpcService`. Machine-token issue validates registered client credentials and audience allowlists; delegated exchange additionally requires a verified user JWT matching the actor and tenant. Session RPCs require specific authorities. A local Netty server test verifies registration of all five services, the five token/session RPC methods, successful issue dispatch, and unauthenticated delegated-exchange rejection. Cross-host gRPC still requires TLS/mTLS and private network controls; the default deployment is loopback-only.

## Inbound interfaces

- **HTTP authentication:** `AuthController` exposes registration (when enabled), login, bootstrap-superadmin, refresh, logout, MFA verify/enrollment, and credential flows under `/api/v1/auth/**`.
- **HTTP authentication/OAuth edge:** `AuthController` continues to serve public login, registration, MFA, refresh/logout, and JWKS over HTTP; Gateway proxies these edge operations. They are not internal administration APIs.
- **gRPC:** `IdentityDirectoryService`, `IdentityAdministrationService`, `PlatformAuthorizationAdministrationService`, `IdentityCredentialService`, and `IdentityTokenService`. The exact contract is in `identity.proto`; method authorities and explicit public methods are configured in `IdentityGrpcSecurityConfiguration`.
- **MQTT commands:** optional inbox/dispatcher path is disabled by default; when enabled it dispatches application-service commands rather than writing repositories from the transport adapter.

## Token and refresh-session behavior

- `TokenGrantFacade` is the current issuance path used by `AuthService` and `ServiceTokenService`. It uses `TokenSigner`, `RefreshSessionPort`, and `TokenGrantAudit`; a source architecture test guards business calls to legacy issue methods still present on `TokenService`.
- User JWTs are RS256 and capped at 15 minutes. The `TokenService` claim builder emits standard identity claims, scopes/roles and authorization versions, with `sid`, `auth_time`, and `amr` when present in the token context. Service tokens carry `client_id`; wildcard scopes are rejected. Delegated service tokens carry a signed actor claim after actor verification.
- JWT claim names and the current `identity_claims_version` are shared through `smartfarm-common-kernel`'s `TokenClaimContract`. New Identity/shared issuer tokens use version 1; resource-server validation accepts legacy tokens without this marker and rejects unknown versions.
- Refresh sessions and token hashes have JPA entities/repositories. `JpaRefreshSessionAdapter` creates, resolves, rotates, and revokes them. Rotation detects reuse and calls `RefreshReplayRevocationService`, which commits a session revocation in a `REQUIRES_NEW` transaction before the request fails.
- `RefreshSessionAdministrationService` backs `ListSessions`, `RevokeSession`, and `RevokeAllSessions`. Session responses contain identifiers/metadata, not token material. Service-registration and authority-policy coverage exist, but tenant isolation at the gRPC boundary and concurrent PostgreSQL replay/rotation proof remain gaps.

## Local runtime verification

The dev YAML declares three service clients. A profile binding regression was fixed: the nested `Client` record has an explicit compatibility constructor, so its canonical constructor now uses `@ConstructorBinding`; the profile-loading test confirms all three clients bind. `auth_time` is serialized as NumericDate epoch seconds and covered by token decoding tests. Gateway now calls `ExchangeDelegatedToken` over gRPC; Order and Reporting call `IssueServiceToken` over gRPC. Service and interceptor tests cover actor/tenant binding, credential rejection, and session authority policies. Local server registration and token RPC reachability are verified with mocked service dependencies; full Spring Boot multi-service runtime and cross-host TLS/mTLS remain unverified.

## Authentication and bootstrap facts

- Login resolves tenant membership from email when no tenant is supplied; multiple active memberships require explicit selection.
- Bootstrap is one-time and transaction-annotated. A seeded singleton database row is locked before checking for an existing super-admin, serializing concurrent requests across Identity instances. Flyway seeds it in production; startup initializes it when Hibernate manages the local schema. A concurrent PostgreSQL integration test is present but remains to be run. Bootstrap no longer creates or logs a TOTP enrollment URI; enrollment is initiated separately through the challenge-token-protected MFA endpoints.
- Account, profile, tenant membership, and role/membership structures are distinct. Existing REST auth endpoints should not be mistaken for general user/role CRUD; administrative operations are represented by gRPC contracts.

## Persistence and configuration

- Dev defaults to `IDENTITY_DB_URL` with local fallback `jdbc:postgresql://localhost:5432/smartfarm`; Hibernate uses `ddl-auto: update`, Flyway is disabled in `application-dev.yml`, and `open-in-view` is true.
- Production requires `IDENTITY_DB_URL/USER/PASSWORD`, enables Flyway, uses Hibernate `ddl-auto: validate`, and defaults both HTTP/gRPC binds to loopback.
- Refresh-session persistence is relational JPA; do not describe refresh families as Redis-backed.
- gRPC TLS is not configured for the single-host loopback default. Any cross-host gRPC deployment must enable TLS/mTLS before widening bind addresses. MQTT envelope signing/verification is enabled in dev with a shared development secret and disabled by default in production; broker authentication/ACL/TLS are separate controls and are not enabled by the application profile alone.

## Open work (verified against current source)

1. Add server-level registration tests for all five `IdentityTokenService` RPCs and prove Gateway, Order, and Reporting issuance against a running gRPC server. Unit interceptor tests cover current method-policy enforcement.
2. Configure TLS/mTLS and certificate identity validation before supporting cross-host gRPC; keep loopback binding for local/single-host deployments.
3. Run the concurrent PostgreSQL first-super-admin integration test and verify exactly one account is created.
4. Prove replay revocation, refresh rotation races, and session RPC authorization/isolation in PostgreSQL/gRPC integration tests.
5. Close remaining permission ownership, mutation/version/outbox, paging/search, and identity-proto model gaps tracked in [`IDENTITY_BACKLOG_UPDATED.md`](../../IDENTITY_BACKLOG_UPDATED.md).

[Identity flows — target vs current](identity-flows.md) · [REST API](../api/rest-api.md) · [gRPC API](../api/grpc-api.md) · [Security architecture](../security/security-architecture.md) · [Identity backlog](../../IDENTITY_BACKLOG_UPDATED.md)
