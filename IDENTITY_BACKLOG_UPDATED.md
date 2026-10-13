# Identity Service Unification Backlog

> Scope: SmartFarm Identity Service  
> Updated: 2026-10-13  
> Strategy: clean rebuild, no legacy schema compatibility  
> Progress legend: `[x]` complete, `[-]` partial/scaffolded, `[ ]` pending, `[N/A]` removed by architecture decision

## Current progress summary

- Completed: 16 items
- Partial/scaffolded: 18 items
- Pending: 55 items
- Removed by clean-rebuild decision: 3 items
- Current focus: wire `IdentityTokenService` into the gRPC runtime and security policy, then implement refresh-session persistence.

## Working rules

- [-] REST is limited to public authentication flows. Public auth is REST, but the legacy internal service-token endpoint still exists.
- [-] Internal read/write operations use Identity gRPC. Most administration is gRPC; session implementation remains pending.
- [x] MQTT is an adapter and invokes application services rather than repositories.
- [ ] No transport adapter mutates business repositories directly. Direct role assignment paths remain.
- [-] All token grants pass through one token facade. Facade exists but legacy callers still issue tokens directly.
- [-] Authorization mutations update versions and outbox atomically. Coverage is incomplete for update/delete paths.
- [x] Proto additions preserve existing services, messages, field numbers, package, and `IdentityProto` outer classname.

---

## EPIC ID-01: Establish identity boundaries

### ID-01.1 Permission ownership

- [-] **P0** Decision made: permission catalog is global and platform-owned.
- [ ] **P0** Record the decision as an ADR in the repository.
- [ ] **P0** Remove tenant permission create/update/delete RPCs or redirect them to platform administration.
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

- [-] **P0** `TokenGrantFacade` introduced as a scaffold.
- [x] **P0** Grant types defined: user login, MFA, refresh, bootstrap, service client, delegated service.
- [-] **P0** Validated `TokenContext` introduced.
- [ ] **P0** Route `AuthService` issuance through `TokenGrantFacade`.
- [ ] **P0** Route `ServiceTokenService` issuance through `TokenGrantFacade`.
- [ ] **P1** Restrict `TokenService` to signing, JWKS, decoding, and claim serialization.
- [ ] **P1** Move grant policy and repository access out of `TokenService`.
- [ ] **P0** Prove no class except the facade reaches the signer.

### ID-02.2 Standardize claims

- [ ] **P0** Publish a versioned token-claim contract.
- [x] **P0** Standard claims exist: `sub`, `tenant_id`, `aud`, `token_type`, `jti`, `iat`, `nbf`, `exp`.
- [x] **P0** User token contains scopes, roles, subject version, and tenant version.
- [x] **P0** Service token has `client_id` and rejects wildcard scope.
- [x] **P0** Delegated service token has signed `act` and actor versions.
- [ ] **P1** Add `sid`, `auth_time`, and `amr` to user tokens.
- [ ] **P1** Enforce token type at downstream service boundaries.

### ID-02.3 Service-token issuance

- [x] **P0** Reject actor delegation when the client has not enabled delegation.
- [-] **P0** `IdentityTokenService` contract merged without removing existing proto symbols.
- [ ] **P0** Implement and register `IdentityTokenServiceGrpc` runtime adapter.
- [ ] **P0** Protect token issuance with authenticated private gRPC/mTLS.
- [ ] **P0** Retire HTTP `permitAll` service-token issuance.
- [ ] **P0** Move service credentials to a secret manager and support rotation.
- [-] **P1** Structured token audit exists; metrics and rate limits remain.

---

## EPIC ID-03: Refresh sessions and revocation

### ID-03.1 Session model

- [-] **P0** Clean schema includes `sf_refresh_session` and `sf_refresh_token`.
- [-] **P1** Clean schema contains lifecycle timestamps and replacement link.
- [ ] **P1** Add client/device metadata.
- [ ] **P0** Implement JPA entities and repositories.
- [ ] **P0** Implement `RefreshSessionPort`.
- [ ] **P0** Distinguish replay from administrative revocation.
- [ ] **P0** Revoke the family in a transaction that commits when replay returns 401.
- [-] **P1** Expiry indexes exist; cleanup job remains.

### ID-03.2 Session gRPC

- [-] **P1** `ListSessions` exists in proto only.
- [-] **P1** `RevokeSession` exists in proto only.
- [-] **P1** `RevokeAllSessions` exists in proto only.
- [x] **P1** Session proto never exposes a raw token or token hash.
- [ ] **P1** Implement session RPCs after persistence is complete.

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
- [-] `IdentityTokenService.ListSessions`, `RevokeSession`, `RevokeAllSessions`: contract only.
- [-] Internal token issue/exchange RPCs: contract complete, runtime pending.
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

- [ ] **P0** Make first-superadmin bootstrap atomic.
- [ ] **P0** Add concurrent PostgreSQL bootstrap test.
- [x] **P0** Stop automatic bootstrap TOTP URI/secret logging.
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

- [ ] Rewrite README to match implementation.
- [ ] Remove nonexistent REST administration endpoints.
- [ ] Correct MFA/gRPC/outbox/PostgreSQL statements.
- [ ] Correct development database documentation.
- [-] TLS configuration is fixed; README instructions remain.
- [ ] Document claims, sessions, rotation, replay, and revocation.
- [-] Proto documents internal token service; architecture documentation remains.

---

## EPIC ID-10: Test/release gates

- [-] Existing gRPC services have policies; new token/session RPC policies remain.
- [-] Existing delegated actor/correlation tests exist; token RPC tests remain.
- [-] Wildcard tests exist; full grant claim-contract tests remain.
- [ ] Permission ownership tests.
- [ ] Proto compatibility CI.
- [ ] Concurrent refresh rotation/replay test.
- [ ] Concurrent bootstrap test.
- [ ] Concurrent authorization-version test.
- [ ] Inbox/outbox multi-worker test.
- [-] Fresh Flyway + Hibernate validate infrastructure exists; clean baseline must pass it.
- [N/A] Upgrade migration test from legacy V1/V2.

## Next execution phase

1. Implement `IdentityTokenGrpcService` for service-token and delegated-token issuance.
2. Register method policies for all five `IdentityTokenService` RPCs.
3. Add token/session authority constants.
4. Explicitly return `UNIMPLEMENTED` for session RPCs until session persistence is ready, rather than silently inheriting default behavior.
5. Build and test.
6. Next phase: implement refresh-session JPA, `RefreshSessionPort`, and session RPCs.
