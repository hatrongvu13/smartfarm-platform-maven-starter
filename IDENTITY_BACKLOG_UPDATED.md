# Identity Service Unification Backlog

> Scope: SmartFarm Identity Service, current implemented product boundary (not a general-purpose OAuth/OIDC or user-directory platform)
> Updated: 2026-10-15
> Strategy: clean rebuild, no legacy schema compatibility  
> Progress legend: `[x]` complete, `[-]` partial/scaffolded, `[ ]` pending, `[N/A]` removed by architecture decision

## Current progress summary

- Completed: **52**
- Partial/scaffolded: **30**
- Pending: **21**
- Deferred/out of current scope: **7**
- Total in-scope checklist entries: **103** (110 marked entries including deferred/N/A)
- Remaining in-scope work: **51 entries** (30 partial, 21 pending); several partial entries are release evidence rather than new implementation.
- Authority: implementation and tests under `services/smartfarm-identity-service`, plus the contract in `libs/smartfarm-proto/src/main/proto/smartfarm/identity/v1/identity.proto`. A contract or bean alone is not proof of secure runtime exposure or production readiness.
- Checklist completion: **50%** (52/103; partial items are not counted as complete).
- Runtime/source status (2026-10-15): Identity has public authentication/JWKS over HTTP and internal administration/token issuance over gRPC. Gateway delegated exchange uses a verified user JWT; Order/Reporting use allowlisted client credentials. The complete Spring Boot context starts with the test profile/H2 and registers all five gRPC adapters; real Gateway/Order/Reporting calls remain unverified. Identity binds gRPC to loopback by default; cross-host deployment is unsupported until TLS/mTLS is configured. Bootstrap has a row-lock implementation and PostgreSQL concurrency test, but the PostgreSQL gates have not been run because Docker is unavailable.
- Scope decision: release readiness means the existing SmartFarm auth, tenant membership, authorization, MFA, refresh-session, and service-token flows work reliably. New directory/farm-scope/user-lifecycle RPCs and OAuth/OIDC compliance are product extensions, not blockers unless separately requested.
- Current focus: close downstream/PostgreSQL release evidence, remaining transaction/version/outbox gaps, and production deployment controls. Tenant-user paging/search now runs in the database. Do not count future API wishlist items as unfinished Identity implementation.

## Target architecture (fixed scope)

- HTTP serves authentication, JWKS, health, and operations; Identity is not an OAuth/OIDC authorization server.
- Internal administration and service-token operations use gRPC. All five adapters have local registration/security coverage; Boot-context, downstream-call, and cross-host transport proof remain release gates.
- MQTT is an adapter to application services; current token issuance flows use `TokenGrantFacade`.
- Authorization mutations must commit version changes and invalidation events atomically; coverage is incomplete for some update/delete paths.
- Preserve existing proto services, messages, field numbers, package, and `IdentityProto` outer classname.

---

## EPIC ID-01: Establish identity boundaries

### ID-01.1 Permission ownership

- [x] **P0** Permission catalog is global and platform-owned.
- [x] **P0** Record the decision as an ADR in the repository (`docs/adr/ADR-001-global-permission-catalog.md`).
- [x] **P0** Tenant permission create/update/delete RPCs are deprecated and rejected; Gateway catalog CRUD routes to platform administration gRPC.
- [N/A] **P0** Tenant-scoped permission schema.
- [x] **P0** Remove the current hybrid behavior completely; tenant RPCs reject mutation and tenant-scoped application mutation APIs are removed.
- [x] **P0** Enforce ownership in schema, services, and tests: global schema, platform administration boundary, all-tenant invalidation, and release guards are present.

### ID-01.2 Channel responsibilities

- [x] **P0** Keep public authentication in HTTP only (not OAuth/OIDC); internal administration is gRPC-only.
- [-] **P0** Keep internal administration in gRPC; adapters and full test-profile/H2 application context are verified, while real downstream calls remain open.
- [x] **P0** MQTT commands call application services.
- [ ] **P1** Add architecture tests preventing repository injection into transport adapters.

---

## EPIC ID-02: Unify token issuance

### ID-02.1 Single token grant facade

- [x] **P0** `TokenGrantFacade` is the active issuance facade for current user and service-token flows.
- [x] **P0** Grant types defined: user login, MFA, refresh, bootstrap, service client, delegated service.
- [x] **P0** Validated `TokenContext` is used by the grant facade.
- [x] **P0** Route `AuthService` issuance and refresh rotation through `TokenGrantFacade`.
- [x] **P0** Route `ServiceTokenService` issuance through `TokenGrantFacade`.
- [ ] **P1** Restrict `TokenService` to signing, JWKS, decoding, and claim serialization.
- [ ] **P1** Move grant policy and repository access out of `TokenService`.
- [x] **P0** Architecture guard rejects business-service calls to legacy issue methods; `TokenGrantFacade` is the only current `TokenSigner` caller.

### ID-02.2 Standardize claims

- [x] **P0** Publish `TokenClaimContract` version 1 in `smartfarm-common-kernel`; Identity and shared JWT issuers publish it, resource servers reject unknown versions while accepting legacy tokens without the version claim.
- [x] **P0** Standard claims exist: `sub`, `tenant_id`, `aud`, `token_type`, `jti`, `iat`, `nbf`, `exp`.
- [x] **P0** User token contains scopes, roles, subject version, and tenant version.
- [x] **P0** Service token has `client_id` and rejects wildcard scope.
- [x] **P0** Delegated service token has signed `act` and actor versions.
- [x] **P1** Add `sid`, `auth_time`, and `amr` to user token claims when present in `TokenContext`.
- [ ] **P1** Enforce token type at downstream service boundaries.

### ID-02.3 Service-token issuance

- [x] **P0** Reject actor delegation when the client has not enabled delegation.
- [x] **P0** `IdentityTokenService` contract and all five RPCs exist without removing existing proto symbols.
- [-] **P0** `IdentityTokenGrpcService` implements all five proto RPCs. Machine issue validates allowlisted client credentials; delegated exchange additionally requires a JWT-bound actor/tenant. Service, interceptor, live-server, and full test-profile context tests cover credentials, delegated identity, method policy, registration, and dispatch; real multi-service calls remain open.
- [-] **P0** Machine token issue is gRPC client-credential authenticated on a loopback listener; delegated exchange additionally requires a valid caller JWT. Local server registration and test-profile startup are verified; cross-host TLS/mTLS remains a production gate.
- [x] **P0** Retire duplicate HTTP service-token issuance; Gateway, Order, and Reporting now use the Identity gRPC token service.
- [ ] **P0** Require externally supplied service credentials and a documented/rehearsed rotation path; secret-manager vendor integration is deployment-specific.
- [-] **P1** Structured token audit exists; metrics and rate limits remain.

---

## EPIC ID-03: Refresh sessions and revocation

### ID-03.1 Session model

- [-] **P0** Clean schema includes `sf_refresh_session` and `sf_refresh_token`.
- [-] **P1** Clean schema contains lifecycle timestamps and replacement link.
- [x] **P0** Implement JPA entities and repositories for refresh sessions and token hashes.
- [x] **P0** Implement `RefreshSessionPort` with JPA persistence and rotation.
- [x] **P0** Distinguish replay (`REFRESH_TOKEN_REUSE`) from administrative revocation reasons.
- [-] **P0** Replay revocation uses a `REQUIRES_NEW` transaction before returning an error; add PostgreSQL integration coverage proving the revoked session and unusable refresh-token family remain committed after the request fails.
- [x] **P1** Expiry indexes and scheduled cleanup job exist; PostgreSQL execution/retention behavior remains part of integration validation.

### ID-03.2 Session gRPC

- [x] **P1** `ListSessions` is implemented by the gRPC adapter and session administration service.
- [x] **P1** `RevokeSession` is implemented by the gRPC adapter and session administration service.
- [x] **P1** `RevokeAllSessions` is implemented by the gRPC adapter and session administration service.
- [x] **P1** Session proto never exposes a raw token or token hash.
- [-] **P1** Session RPCs and authority policies exist; registration is tested, but gRPC-level tenant-isolation coverage remains.

### ID-03.3 Access-token invalidation

- [-] **P0** Authorization versions exist; enforcement is not complete.
- [-] **P0** Subject and tenant mutation methods exist; coverage is incomplete.
- [N/A] **P1** Decide whether login lock revokes existing access authorization: current access tokens are short-lived and lockout gates new authentication; immediate token revocation is outside the current contract.

---

## EPIC ID-04: Complete the Identity proto contract

### ID-04.1 Model gaps

- [N/A] **P0** `country_code` and `address` are legacy proto fields without current product/domain support; defer until profile requirements are approved.
- [ ] **P0** Add authorization versions to principal/authorization responses.
- [ ] **P1** Add membership update/version fields.
- [ ] **P1** Add expected versions to role, permission, and account mutations.
- [x] **P1** Use `Locale.ROOT` for normalization.
- [x] **P1** Reuse injected `IdentityProtoMapper`.
- [N/A] **P1** Replace authenticator type/status strings with enums: retain the existing proto v1 string fields for wire compatibility; consider enums only in a versioned contract change.
- [ ] **P1** Add structured gRPC error details.
- [x] **P1** Align permission validation across gRPC, tenant/platform role mutation, and startup synchronization with domain `PermissionCode`.

### ID-04.2 Contract coverage

- [x] `IdentityTokenService.ListSessions`, `RevokeSession`, `RevokeAllSessions`: adapter and application implementation exist.
- [-] Internal token issue/exchange RPCs: adapter and client-secret validation exist; machine issue does not require a pre-existing bearer JWT, while delegated exchange requires a verified user JWT. Local server and full Boot-context tests pass; downstream proof remains.
- [x] Platform permission CRUD: Get/Create/Update/Delete global permission RPCs are implemented and method-protected.

### ID-04.3 Deferred contract extensions (not release blockers)

- Separate Get/Update/Delete-or-anonymize user and explicit invitation/removal RPCs.
- Farm-scope grant/revoke/list RPCs; current farm authorization remains in its existing authorization boundary.
- Admin password-reset RPC and principal-membership listing.
- Per-session client/device metadata.
- OAuth/OIDC protocol compliance and discovery/PKCE/browser SSO.
- Versioned proto enum replacements for authenticator/status strings.

---

## EPIC ID-05: User and membership paths

### ID-05.1 User/profile

- [-] **P0** Account, profile, and membership are separate entities; command boundary remains fragmented.
- [ ] **P0** Route all account/profile entry points through shared command services.
- [-] **P1** Entities use optimistic versions; not all RPCs expose expected/new versions.
- [x] **P1** Password timestamps use injected `Clock` where patched.
- [x] **P0** Reconcile password maximum length: Identity limits passwords to at most 72 UTF-8 bytes and routes oversized login comparisons through the dummy hash.
- [x] **P0** Replace in-memory user filtering with database paging/search; filtering, ordering, and offset-based slicing now execute in the database.
- [ ] **P0** Separate create-account from link-existing-account.

### ID-05.2 Membership/farm scope

- [-] **P0** Membership mutations mostly use `TenantMembershipService`.
- [x] **P0** Revoke tenant refresh sessions after suspend/disable; account disable revokes account sessions.
- [-] **P1** Actor/correlation/reason are propagated on many paths; idempotency remains missing.

---

## EPIC ID-06: Role and permission paths

- [x] **P0** Remove direct initial-role save from tenant user creation; initial assignment now goes through `RoleManagementService`, which atomically records the role event and authorization invalidation.
- [x] **P0** Remove direct role save from public registration; default USER assignment now uses `RoleManagementService`.
- [x] **P0** MQTT role revoke carries actor and correlation.
- [-] **P0** Role/permission events and versions are implemented on some, not all, mutations.
- [ ] **P1** Persist role description or remove the proto field.
- [-] **P1** Authorization synchronizer marks configured roles as system roles, but currently updates matching roles regardless of their existing ownership; protect user-managed roles before synchronization can overwrite them.
- [x] **P0** Remove `SuperAdminWildcardGrantRunner` in the clean rebuild.
- [N/A] Convert startup runner to an upgrade migration.

---

## EPIC ID-07: Bootstrap and MFA

- [-] **P0** Make first-superadmin bootstrap atomic with a database row lock; PostgreSQL concurrency proof remains pending.
- [-] **P0** Add concurrent PostgreSQL bootstrap test; execution against PostgreSQL remains pending.
- [x] **P0** Stop bootstrap TOTP URI/secret logging; bootstrap no longer generates or logs an enrollment URI.
- [-] **P0** Use authenticated post-bootstrap MFA enrollment; document and test it.
- [ ] **P1** Remove stale bootstrap configuration.
- [ ] **P1** Persist pending enrollment expiry or bind it to a challenge.
- [ ] **P1** Cleanup expired pending authenticators/challenges.
- [ ] **P1** Add replay/concurrency/recovery-code tests.

---

## EPIC ID-08: Immediate correctness/configuration

- [x] **P0** Enable SSL in `application-dev-tls.yml`.
- [x] **P1** Inject and reuse `IdentityProtoMapper`.
- [x] **P1** Remove duplicate/unused logger imports.
- [x] **P1** Use injected clock for password timestamps.
- [-] **P0** Disable Open Session in View for all profiles; transaction-boundary review remains incomplete.
- [ ] **P1** Fail startup when MQTT commands are enabled without verification.
- [N/A] **P1** Require PostgreSQL `FOR UPDATE SKIP LOCKED`: current row locking is sufficient for correctness; use skip-locked only if measured multi-worker contention requires it.
- [-] **P1** Clean baseline adds core FKs; runtime validation and orphan tests remain.

---

## EPIC ID-09: Documentation

- [x] Rewrite README to match implementation (reconciled 2026-10-15; source-specific gaps remain tracked below).
- [x] Remove nonexistent REST administration endpoints from the REST API guide.
- [-] Correct MFA/gRPC/outbox/PostgreSQL statements across the service, security, API, and operations guides; continue source-by-source review of remaining project docs.
- [x] Correct identity development database and profile behavior in operations docs.
- [x] Document token claims, session persistence, rotation, replay, and revocation in identity/API docs.
- [-] Proto contract and gRPC adapter documented; secure transport and runtime-level test evidence remain outstanding.

---

## EPIC ID-10: Test/release gates

- [-] Existing services have method policies; token issue/exchange and session-method enforcement are covered through `JwtServerInterceptor` tests. Local registration and test-profile Boot context pass; multi-service runtime remains.
- [-] Token RPC credential/delegation/method-security unit and live-server tests exist; full Spring context and downstream-client proof remains.
- [-] Wildcard tests exist; full grant claim-contract tests remain.
- [-] Permission ownership tests: static release guard exists; PostgreSQL transaction/invalidation coverage remains.
- [ ] Proto compatibility CI.
- [ ] PostgreSQL concurrent refresh rotation/replay and commit-on-error tests.
- [-] Concurrent bootstrap PostgreSQL test is present but has not been executed.
- [ ] Concurrent authorization-version test.
- [ ] Inbox/outbox multi-worker test.
- [-] Fresh Flyway + Hibernate validate infrastructure exists; clean baseline must pass it.
- [N/A] Upgrade migration test from legacy V1/V2.

## Next execution plan (ranked)

1. **P0 release evidence — downstream and PostgreSQL.** Test-profile Spring Boot startup and gRPC registration pass; verify real Gateway/Order/Reporting calls, then run Flyway/Hibernate validation plus bootstrap, refresh replay/rotation, session cleanup/isolation, and permission invalidation integration tests on PostgreSQL. H2/unit tests do not replace these gates.
2. **P0 deployment gate — production secrets and transport.** Require externally supplied/rotatable service credentials and MFA encryption key; keep gRPC loopback-only unless TLS/mTLS is enabled for cross-host deployment. Secret-manager vendor integration is deployment-specific, not an Identity code abstraction.
3. **P1 functional correctness.** Replace in-memory tenant-user filtering with database paging/search; disable Open Session in View and make transaction boundaries explicit; close remaining authorization version/outbox gaps and validate all supported mutations.
4. **P1 operational resilience.** Add PostgreSQL concurrent refresh/bootstrap and outbox/inbox multi-worker proof; validate MFA pending-enrollment expiry, challenge cleanup, replay, and recovery-code concurrency.
5. **P2 contract/release polish.** Add proto compatibility CI and structured gRPC error details; complete documentation reconciliation. Keep new user-lifecycle/farm-scope RPCs and v1 enum changes deferred until requirements and compatibility policy are approved.

## Remaining effort estimate

Estimates are implementation effort for one engineer, excluding review/release scheduling and external infrastructure provisioning:

- **P0 release evidence (about 2–4 engineer-days):** downstream gRPC calls; PostgreSQL Flyway/validate; bootstrap and refresh/session/permission transaction tests (test-profile application startup is verified).
- **P0 production deployment controls (about 1–3 engineer-days, infra-dependent):** inject/rotate service and MFA keys; prove loopback-only deployment or configure cross-host TLS/mTLS. Secret-manager vendor setup is outside this code estimate.
- **P1 functional/data correctness (about 2–5 engineer-days):** remaining transaction cleanup; complete authorization version/outbox coverage and paging for other catalogs.
- **P1 resilience and release polish (about 2–4 engineer-days):** MFA lifecycle/concurrency tests, outbox/inbox multi-worker validation, proto compatibility and gRPC error details.

**Current readiness:** core feature shape is implemented, but production readiness is not proven until both P0 gates pass. Estimates assume PostgreSQL and deployment infrastructure are available; infrastructure or contract changes can increase them.

## Reconciled checklist totals

There are **52 complete + 30 partial + 21 pending = 103 in-scope entries**, plus **7 N/A/deferred entries** (110 marked entries total). Completion is **50%** (complete / in-scope); partial work is not counted as complete. Keep this summary synchronized with the checklist.
