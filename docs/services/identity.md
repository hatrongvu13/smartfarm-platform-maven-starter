# Service: Identity (`services/smartfarm-identity-service`)

> Verified @ HEAD `eaaa112`. :8092 (HTTP) / :9092 (gRPC). ← [System Overview](../architecture/system-overview.md)

## Trách nhiệm
Authentication, authorization (RBAC), MFA (TOTP), account/membership/tenant, token issuance (RS256 + JWKS), platform authorization admin.

## Inbound interface
- **REST** `AuthController`: `/api/v1/auth/{login,register,refresh,logout,bootstrap-superadmin,mfa/verify,mfa/enrollment/begin,mfa/enrollment/confirm}`.
- **gRPC** (4 service):
  - `IdentityDirectoryService`: GetPrincipal, UpdatePrincipalProfile, CheckPermission, BatchCheckPermissions, Ping
  - `IdentityAdministrationService`: ListRoles, GetRole, ListPermissions, ListUsers, GetUserAuthorization, CreateUser, AssignRole, RevokeRole, Disable/Enable/SuspendMembership
  - `PlatformAuthorizationAdministrationService`: ListPlatformRoles, CreatePlatformRole, Grant/RevokePlatformPermission
  - `IdentityCredentialService`: GetSecurityProfile, BeginTotpEnrollment, ConfirmTotpEnrollment, DisableOwnMfa, RegenerateRecoveryCodes, ResetUserMfa, ChangeOwnPassword

## Outbound dependency
- Postgres (own DB, env-parameterized `IDENTITY_DB_URL`), Redis (refresh token families).
- MQTT outbox (⚠️ **JSON** payload — EVT-01).

## Event publish/consume
- Publish: identity domain events (JSON) + `identity.command.result` (**orphan, no consumer** — ISSUE-06).
- Consume: MQTT command inbox (`IdentityMqttCommandDispatcher`).

## Auth model
RS256 JWT (15m), rotating refresh + family reuse-detection, TOTP MFA (encrypted secret, hashed recovery/challenge, lockout), login lockout + timing defense, DB RBAC (role→permission + per-farm scope).

## Database
`V1__identity_baseline.sql` (1 migration). Only service env-parameterized for per-service DB split.

## Known limitations
- Event payload JSON ≠ protobuf chuẩn (EVT-01, HIGH).
- Topic schema khác chuẩn (EVT-02).
- `identity.command.result` orphan producer (ISSUE-06).
- `isSuperAdmin()` SCOPE_* bypass (ISSUE-12).

## TODO
- [ ] Chuyển event sang protobuf DomainEvent / align topic schema.
- [ ] Consumer cho command.result hoặc bỏ.

[Auth flow](../architecture/request-flows.md#1-login) · [Security](../security/security-architecture.md)


---

## Auth model — updated 2026-10-07 (verified live against dev)

**First super-admin via endpoint (no auto-create).** The old `DevSuperAdminBootstrap` (auto-created a
random tenant/user with a YAML password) is **removed**. The first system super-admin is seeded once via
`POST /api/v1/auth/bootstrap-superadmin {email, password}`:
- Works ONLY while the system has no super-admin; a second call returns **409** ("bootstrap is closed").
- Creates the `system` tenant + user + `SUPERADMIN` role + membership, grants the wildcard permission
  `*`, and returns a **COMPLETED access token immediately** (no TOTP gate — "D1").
- Prints an `otpauth://` TOTP enrollment URI to the service **console** on first bootstrap (never over
  HTTP), via an `afterCommit` hook so a TOTP failure cannot roll back the created admin.

**Login is email + password only.** `POST /api/v1/auth/login {email, password}` — `tenantId` is optional.
The tenant is resolved from the email: a single active membership logs in straight away; several return
`TENANT_SELECTION_REQUIRED` with a `tenants[]` list (`{tenantId, tenantCode, tenantName}`) to choose from
(resend with `tenantId`). SUPERADMIN/ADMIN/PLATFORM_ADMIN are still forced through TOTP
(`MfaRequirementService`).

**Super-admin authority.** A super-admin token carries `scope:"*"`. The gateway authorizes via
`@PreAuthorize("hasAuthority('SCOPE_x')")`, and Spring Security 7 matches authority **strings**, so a bare
`SCOPE_*` does not satisfy a specific `SCOPE_x` check. `JwtAuthorities.authorities()` therefore **expands**
`*` into the concrete known gateway scope set (`JwtAuthorities.KNOWN_SCOPES`) — keep that set in sync when
adding a new `hasAuthority('SCOPE_...')` check. A startup `SuperAdminWildcardGrantRunner` retroactively
grants `*` to any pre-existing SUPERADMIN role (idempotent).

**Single-ingress binding.** Identity (and every service) binds HTTP (and gRPC) to `127.0.0.1` by default in
BOTH dev and prod — the gateway is the sole public ingress. Override per host with
`IDENTITY_BIND_ADDRESS` / `*_BIND_ADDRESS` / `*_GRPC_BIND_ADDRESS` for a cross-host mesh.

**Fixes landed alongside:** (1) `IdentityMqttHealthIndicator` no longer NPEs on a null detail value;
(2) `jackson-datatype-jsr310` added so integration-event payloads with `java.time.Instant` serialize
(this had broken every event-publishing path, e.g. TOTP enrollment confirm → 401);
(3) `beginTotpEnrollment` supersedes a stale PENDING enrollment instead of dead-ending.
