# Identity Service Unification Backlog

> Scope: SmartFarm Identity Service  
> Updated: 2026-10-15
> Strategy: clean rebuild, no legacy schema compatibility  
> Progress legend: `[x]` complete, `[-]` partial/scaffolded, `[ ]` pending, `[N/A]` removed by architecture decision

## Current progress summary

- Completed: **43**
- Partial/scaffolded: **32**
- Pending: **49**
- Removed by clean-rebuild decision (N/A): **3**
- Total checklist entries: **127**
- Authority: implementation and tests under `services/smartfarm-identity-service`, plus the contract in `libs/smartfarm-proto/src/main/proto/smartfarm/identity/v1/identity.proto`. A contract or bean alone is not proof of secure runtime exposure or production readiness.
- Checklist completion: **34%** marked complete (43/127; partial items are not counted as complete).
- Runtime/source status (2026-10-15): Identity binds three dev service clients; service-token issuance is now gRPC-only. Gateway uses `ExchangeDelegatedToken` with a verified user JWT, and Order/Reporting use `IssueServiceToken` with client credentials. All five Identity adapters are marked `@GrpcService`; a local Netty server registration test verifies all five services, all five token RPC methods, successful machine-token RPC dispatch, and rejection of unauthenticated delegated exchange. Full Spring Boot application-context/multi-service runtime flow remains unverified. Identity gRPC binds to loopback by default; cross-host TLS/mTLS is not configured. First-superadmin creation now locks a seeded singleton database row within the bootstrap transaction; a concurrent PostgreSQL integration test is present but has not been run here.
- Current focus: verify full Spring Boot gRPC runtime wiring and cross-host transport before production; run PostgreSQL bootstrap/refresh concurrency tests; continue permission ownership and mutation/version/outbox coverage.

## Working rules

- [x] REST is limited to public authentication/OAuth-facing flows, JWKS, health, and operations. Internal service-token issuance is not exposed over HTTP.
- [x] Identity internal read/write and token-issuance operations use gRPC; all five adapters now register through Spring gRPC and local live token RPC registration is tested. Full application-context/multi-service runtime and cross-host transport proof remain open.
- [x] MQTT is an adapter and invokes application services rather than repositories.
- [ ] No transport adapter mutates business repositories directly. Direct role assignment paths remain.
- [x] Current authentication and service-token application flows issue through `TokenGrantFacade`; legacy signing helpers remain on `TokenService` but have no current application callers.
- [-] Authorization mutations update versions and outbox atomically. Coverage is incomplete for update/delete paths.
- [x] Proto additions preserve existing services, messages, field numbers, package, and `IdentityProto` outer classname.

---

## EPIC ID-01: Establish identity boundaries

### ID-01.1 Permission ownership

- [-] **P0** Decision made: permission catalog is global and platform-owned.
- [ ] **P0** Record the decision as an ADR in the repository.
- [x] **P0** Tenant permission create/update/delete RPCs are deprecated and rejected; Gateway catalog CRUD routes to platform administration gRPC.
- [N/A] **P0** Tenant-scoped permission schema.
- [ ] **P0** Remove the current hybrid behavior completely.
- [ ] **P0** Enforce ownership in schema, services, and tests.

### ID-01.2 Channel responsibilities

- [-] **P0** Keep public authentication in REST only.
- [-] **P0** Keep internal administration in gRPC only.
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
- [-] **P0** `IdentityTokenService` contract merged without removing existing proto symbols.
- [-] **P0** `IdentityTokenGrpcService` implements all five proto RPCs. Machine issue validates allowlisted client credentials; delegated exchange additionally requires a JWT-bound actor/tenant. Service, interceptor, and live-server tests cover credential rejection, delegated identity, public machine-issue policy, session authorities, registration, and issue/exchange dispatch. Full application-context and multi-service runtime proof remains open.
- [-] **P0** Machine token issue is gRPC client-credential authenticated on a loopback listener; delegated exchange additionally requires a valid caller JWT. Local server registration/reachability is tested; cross-host TLS/mTLS remains a production gate.
- [x] **P0** Retire duplicate HTTP service-token issuance; Gateway, Order, and Reporting now use the Identity gRPC token service.
- [ ] **P0** Move service credentials to a secret manager and support rotation.
- [-] **P1** Structured token audit exists; metrics and rate limits remain.

---

## EPIC ID-03: Refresh sessions and revocation

### ID-03.1 Session model

- [-] **P0** Clean schema includes `sf_refresh_session` and `sf_refresh_token`.
- [-] **P1** Clean schema contains lifecycle timestamps and replacement link.
- [ ] **P1** Add client/device metadata.
- [x] **P0** Implement JPA entities and repositories for refresh sessions and token hashes.
- [x] **P0** Implement `RefreshSessionPort` with JPA persistence and rotation.
- [x] **P0** Distinguish replay (`REFRESH_TOKEN_REUSE`) from administrative revocation reasons.
- [-] **P0** Replay revocation uses a `REQUIRES_NEW` transaction before returning an error; add PostgreSQL integration coverage proving the revoked session and unusable refresh-token family remain committed after the request fails.
- [-] **P1** Expiry indexes exist; cleanup job remains.

### ID-03.2 Session gRPC

- [x] **P1** `ListSessions` is implemented by the gRPC adapter and session administration service.
- [x] **P1** `RevokeSession` is implemented by the gRPC adapter and session administration service.
- [x] **P1** `RevokeAllSessions` is implemented by the gRPC adapter and session administration service.
- [x] **P1** Session proto never exposes a raw token or token hash.
- [-] **P1** Session RPCs and authority policies exist; registration is tested, but gRPC-level tenant-isolation coverage remains.

### ID-03.3 Access-token invalidation

- [-] **P0** Authorization versions exist; enforcement is not complete.
- [-] **P0** Subject and tenant mutation methods exist; coverage is incomplete.
- [ ] **P1** Decide whether login lock revokes existing access authorization.

---

## EPIC ID-04: Complete the Identity proto contract

### ID-04.1 Model gaps

- [ ] **P0** Implement or remove `country_code` and `address` end-to-end.
- [ ] **P0** Add authorization versions to principal/authorization responses.
- [ ] **P1** Add membership update/version fields.
- [ ] **P1** Add expected versions to role, permission, and account mutations.
- [x] **P1** Use `Locale.ROOT` for normalization.
- [x] **P1** Reuse injected `IdentityProtoMapper`.
- [ ] **P1** Replace authenticator type/status strings with enums.
- [ ] **P1** Add structured gRPC error details.
- [ ] **P1** Align permission validation with domain `PermissionCode`.

### ID-04.2 Missing RPCs

- [ ] `ListPrincipalMemberships`
- [ ] `GetUser`, `UpdateUser`, `DeleteOrAnonymizeUser`
- [ ] `InviteUser`, `RemoveMembership`
- [ ] `GrantFarmAccess`, `RevokeFarmAccess`, `ListFarmScopes`
- [ ] `GetPermission`
- [ ] `ResetUserPassword`
- [x] `IdentityTokenService.ListSessions`, `RevokeSession`, `RevokeAllSessions`: adapter and application implementation exist.
- [-] Internal token issue/exchange RPCs: adapter and client-secret validation exist; machine issue does not require a pre-existing bearer JWT, while delegated exchange requires a verified user JWT. Dedicated server-level registration/enforcement tests are absent.
- [ ] Platform permission CRUD.

---

## EPIC ID-05: User and membership paths

### ID-05.1 User/profile

- [-] **P0** Account, profile, and membership are separate entities; command boundary remains fragmented.
- [ ] **P0** Route all account/profile entry points through shared command services.
- [-] **P1** Entities use optimistic versions; not all RPCs expose expected/new versions.
- [x] **P1** Password timestamps use injected `Clock` where patched.
- [ ] **P0** Reconcile password maximum length.
- [ ] **P0** Replace in-memory user filtering with database paging/search.
- [ ] **P0** Separate create-account from link-existing-account.
- [ ] **P1** Add disable/anonymize/retention workflow.

### ID-05.2 Membership/farm scope

- [-] **P0** Membership mutations mostly use `TenantMembershipService`.
- [ ] **P0** Add farm-scope RPCs.
- [ ] **P0** Revoke tenant sessions consistently after suspend/disable.
- [-] **P1** Actor/correlation/reason are propagated on many paths; idempotency remains missing.

---

## EPIC ID-06: Role and permission paths

- [ ] **P0** Remove direct initial-role save from tenant user creation.
- [ ] **P0** Remove direct role save from public registration.
- [x] **P0** MQTT role revoke carries actor and correlation.
- [-] **P0** Role/permission events and versions are implemented on some, not all, mutations.
- [ ] **P1** Persist role description or remove the proto field.
- [-] **P1** Authorization synchronizer is intended for system definitions; ownership marker remains.
- [ ] **P1** Add explicit catalog ownership marker.
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
- [ ] **P0** Disable Open Session in View and fix transaction boundaries.
- [ ] **P1** Fail startup when MQTT commands are enabled without verification.
- [ ] **P1** Use PostgreSQL `FOR UPDATE SKIP LOCKED` for inbox/outbox.
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

- [-] Existing services have method policies; token issue/exchange and session-method enforcement are covered through `JwtServerInterceptor` tests. Local server registration/reachability is tested; full application-context multi-service runtime remains.
- [-] Delegated-actor/correlation tests exist; add token RPC tests covering credential rejection, audience allowlisting, delegation, and method security.
- [-] Wildcard tests exist; full grant claim-contract tests remain.
- [ ] Permission ownership tests.
- [ ] Proto compatibility CI.
- [ ] Concurrent refresh rotation/replay test.
- [-] Concurrent bootstrap PostgreSQL test is present but has not been executed.
- [ ] Concurrent authorization-version test.
- [ ] Inbox/outbox multi-worker test.
- [-] Fresh Flyway + Hibernate validate infrastructure exists; clean baseline must pass it.
- [N/A] Upgrade migration test from legacy V1/V2.

## Next execution plan (ranked)

1. **P0 — Complete gRPC runtime proof.** All five adapters are now Spring-discoverable and a live local Netty test proves registration plus machine issue/delegated-auth boundaries. Next verify the full Spring Boot application context and Gateway/Order/Reporting calls; do not treat mocked-dependency tests as multi-service proof. Starting points: `IdentityGrpcServerRegistrationTest`, `IdentityGrpcSecurityConfiguration`, and `GrpcRequestSecurityDelegatedActorTest`.
2. **P0 — Prove and harden service-token exposure.** Keep the single gRPC issuance boundary and configure TLS/mTLS before cross-host deployment. Keep listener binding private and never log credentials/tokens.
3. **P0 — Prove bootstrap atomicity.** Bootstrap now locks a seeded singleton DB row before checking for an existing super-admin; a concurrent PostgreSQL integration test covers one-winner behavior. Run that test against PostgreSQL before marking the gate complete. Bootstrap no longer generates or logs a TOTP URI; authenticated MFA enrollment is a separate challenge flow.
4. **P1 — Verify refresh replay/session behavior end to end.** Add PostgreSQL tests for `JpaRefreshSessionAdapter.rotate`, `RefreshReplayRevocationService` commit-on-error, replay family rejection, rotation races, revoke/revoke-all, expiry cleanup, and tenant isolation. Extend `RefreshSessionTenantIsolationIT` and `RefreshSessionCleanupIT`; unit tests do not prove transaction behavior.
5. **P1 — Close authorization mutation/version gaps.** Audit `DefaultTenantIdentityAdministrationService`, `RoleManagementService`, `PermissionCatalogMutationService`, and `AuthorizationMutationCoordinator`; test that every supported mutation updates version/outbox atomically and that permission ownership is consistently enforced.
6. **P1/P2 — Finish contract and operations gaps.** Reconcile remaining model/RPC gaps in `identity.proto`, `IdentityProtoMapper`, and gRPC services; add proto compatibility, paging/search, and expected-version tests. Then harden production transport/configuration (gRPC TLS, secret rotation, MQTT broker ACL/TLS, Flyway/profile validation) and document completion only when source and tests prove it.

## Reconciled checklist totals

Totals count checklist entries only (all epics and working rules; exclude headings and prose): **43 complete + 32 partial + 49 pending + 3 N/A = 127 entries**. Recalculate these numbers whenever a marker changes.
