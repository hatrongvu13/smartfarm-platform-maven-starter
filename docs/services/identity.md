# Identity Service (`services/smartfarm-identity-service`)

> Reconciled 2026-10-15 against the service source, its tests, its runtime configuration, and `libs/smartfarm-proto/src/main/proto/smartfarm/identity/v1/identity.proto`. The implementation is authoritative; this page is not a release-readiness claim.

## Responsibility and runtime

Identity owns account/profile, tenant membership, RBAC and permission administration, MFA, JWT issuance/JWKS, service-client validation, and refresh-session lifecycle. HTTP defaults to `127.0.0.1:8092`; gRPC port `9092` is configured in base configuration. Production explicitly binds gRPC to loopback (`IDENTITY_GRPC_BIND_ADDRESS`, default `127.0.0.1`). Dev does not set a gRPC bind address in the identity profile, so do not assume loopback without verifying the Spring gRPC default/runtime.

The service has five gRPC service implementations: directory, administration, platform authorization administration, credentials, and token/session. The token service contract and `IdentityTokenGrpcService` adapter contain all five RPC handlers. Issue/exchange now require a valid caller JWT; session RPCs require specific authorities. gRPC TLS remains disabled by default, and runtime registration/policy tests are still required.

## Inbound interfaces

- **HTTP authentication:** `AuthController` exposes registration (when enabled), login, bootstrap-superadmin, refresh, logout, MFA verify/enrollment, and credential flows under `/api/v1/auth/**`.
- **HTTP machine-token issuance:** `InternalServiceTokenController` exposes `POST /internal/service-token`. The gateway must send a dedicated `X-Identity-Internal-Secret` header in addition to client credentials in the body; the controller compares it in constant time and fails closed if the secret is not configured. Configure the same independently managed `IDENTITY_INTERNAL_API_SECRET` in both gateway and identity (required in prod). Spring's bearer-token filter chain still permits this route, so the dedicated header, private binding, and TLS/mTLS remain essential; retire the HTTP path if it cannot be kept private.
- **gRPC:** `IdentityDirectoryService`, `IdentityAdministrationService`, `PlatformAuthorizationAdministrationService`, `IdentityCredentialService`, and `IdentityTokenService`. The exact contract is in `identity.proto`; method authorities and explicit public methods are configured in `IdentityGrpcSecurityConfiguration`.
- **MQTT commands:** optional inbox/dispatcher path is disabled by default; when enabled it dispatches application-service commands rather than writing repositories from the transport adapter.

## Token and refresh-session behavior

- `TokenGrantFacade` is the current issuance path used by `AuthService` and `ServiceTokenService`. It uses `TokenSigner`, `RefreshSessionPort`, and `TokenGrantAudit`; a source architecture test guards business calls to legacy issue methods still present on `TokenService`.
- User JWTs are RS256 and capped at 15 minutes. The `TokenService` claim builder emits standard identity claims, scopes/roles and authorization versions, with `sid`, `auth_time`, and `amr` when present in the token context. Service tokens carry `client_id`; wildcard scopes are rejected. Delegated service tokens carry a signed actor claim after actor verification.
- Refresh sessions and token hashes have JPA entities/repositories. `JpaRefreshSessionAdapter` creates, resolves, rotates, and revokes them. Rotation detects reuse and calls `RefreshReplayRevocationService`, which commits a session revocation in a `REQUIRES_NEW` transaction before the request fails.
- `RefreshSessionAdministrationService` backs `ListSessions`, `RevokeSession`, and `RevokeAllSessions`. Session responses contain identifiers/metadata, not token material. Tenant-scoped integration tests and cleanup/replay tests exist, but dedicated gRPC authorization/server-registration and concurrent PostgreSQL replay/rotation proof remain gaps.

## Local runtime verification

The dev YAML declares a `gateway` service-token client and a development-only client-secret hash. However, the latest local startup on 2026-10-15 logged `clientCount=0`; `POST /internal/service-token` returned 401 even with the correct shared internal header, and Identity logged `bad_credentials`. Missing and incorrect internal headers also returned 401 as intended. Therefore the shared-header gate is verified, but dev client configuration binding and successful token issuance are **not** verified; the binding cause remains unresolved. Do not treat the configured YAML allowlist as evidence that the local flow currently works.

## Authentication and bootstrap facts

- Login resolves tenant membership from email when no tenant is supplied; multiple active memberships require explicit selection.
- Bootstrap is one-time and transaction-annotated. It no longer creates or logs a TOTP enrollment URI. Enrollment is initiated separately through the challenge-token-protected MFA endpoints.
- Account, profile, tenant membership, and role/membership structures are distinct. Existing REST auth endpoints should not be mistaken for general user/role CRUD; administrative operations are represented by gRPC contracts.

## Persistence and configuration

- Dev defaults to `IDENTITY_DB_URL` with local fallback `jdbc:postgresql://localhost:5432/smartfarm`; Hibernate uses `ddl-auto: update`, Flyway is disabled in `application-dev.yml`, and `open-in-view` is true.
- Production requires `IDENTITY_DB_URL/USER/PASSWORD`, enables Flyway, uses Hibernate `ddl-auto: validate`, and defaults both HTTP/gRPC binds to loopback.
- Refresh-session persistence is relational JPA; do not describe refresh families as Redis-backed.
- gRPC TLS is disabled in the service defaults. MQTT envelope signing/verification is enabled in dev with a shared development secret and disabled by default in production; broker authentication/ACL/TLS are separate controls and are not enabled by the application profile alone.

## Open work (verified against current source)

1. Resolve and test dev `ServiceTokenSettings.clients` binding; prove `clientCount > 0` and successful local token issuance with the shared header.
2. Add TLS/mTLS to the private service-token issuance boundary, decide whether to retire the HTTP endpoint, and add server-level registration and method-policy tests.
3. Add a concurrent PostgreSQL first-super-admin test.
4. Prove replay revocation, refresh rotation races, and session RPC authorization/isolation in PostgreSQL/gRPC integration tests.
5. Close remaining permission ownership, mutation/version/outbox, paging/search, and identity-proto model gaps tracked in [`IDENTITY_BACKLOG_UPDATED.md`](../../IDENTITY_BACKLOG_UPDATED.md).

[REST API](../api/rest-api.md) · [gRPC API](../api/grpc-api.md) · [Security architecture](../security/security-architecture.md) · [Identity backlog](../../IDENTITY_BACKLOG_UPDATED.md)
