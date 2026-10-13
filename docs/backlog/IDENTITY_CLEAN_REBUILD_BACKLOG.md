# Identity Clean Rebuild Backlog

> Strategy: destructive clean rebuild. No compatibility migration and no legacy schema support.

## P0 architecture

- [x] Identity is the only token authority.
- [x] Permission catalog is global and platform-owned.
- [x] Tenant role management consumes the global permission catalog.
- [x] Refresh session is a first-class aggregate.
- [ ] Replace all direct calls to `TokenService.issue*` with `TokenGrantFacade.issue`.
- [ ] Remove tenant permission create/update/delete RPCs from `IdentityAdministrationService`.
- [ ] Add global permission CRUD to `PlatformAuthorizationAdministrationService`.
- [ ] Route REST, gRPC, and MQTT mutations through shared command services.
- [ ] Recreate the identity database with the clean V1 baseline.

## P0 token issuance

- [ ] Wire login completion to `GrantType.USER_LOGIN`.
- [ ] Wire MFA completion to `GrantType.MFA_COMPLETION`.
- [ ] Wire refresh rotation to `GrantType.REFRESH_ROTATION`.
- [ ] Wire first-superadmin issuance to `GrantType.BOOTSTRAP`.
- [ ] Wire machine credentials to `GrantType.SERVICE_CLIENT`.
- [ ] Wire delegated machine credentials to `GrantType.DELEGATED_SERVICE`.
- [ ] Add signer adapter backed by the existing RS256/Nimbus implementation.
- [ ] Reject audience not on the client allowlist.
- [ ] Reject wildcard service scopes.
- [ ] Reject actor input when delegation is disabled.
- [ ] Include subject and tenant authorization versions in user/delegated tokens.

## P0 refresh sessions

- [ ] Implement session creation, rotation, reuse detection, revoke session, and revoke all.
- [ ] Commit family revocation even when replay returns unauthenticated.
- [ ] Add PostgreSQL concurrent rotation tests.
- [ ] Add cleanup for expired sessions/tokens.

## P0 proto

- [ ] Add `IdentityTokenService` from this bundle to the canonical identity proto.
- [ ] Add authorization versions to `Principal` and `UserAuthorization`.
- [ ] Add farm-scope administration RPCs.
- [ ] Add user link/invite/remove-membership semantics.
- [ ] Remove tenant permission mutation RPCs after platform RPCs are live.
- [ ] Implement `country_code` and `address`, or remove them before the clean contract is published.

## P0 security

- [ ] Protect token service with mTLS/private gRPC authentication.
- [ ] Make bootstrap atomic and remove TOTP URI logging.
- [ ] Enable dev TLS and add HTTPS smoke test.
- [ ] Fail startup when MQTT commands are enabled without message verification.
- [ ] Remove secrets/default production credentials from YAML.

## P1 data and performance

- [ ] Replace in-memory user filtering with DB paging/search.
- [ ] Use `FOR UPDATE SKIP LOCKED` for inbox/outbox workers.
- [ ] Add foreign keys for identity core data.
- [ ] Disable Open Session in View and fix transaction boundaries.
- [ ] Add cleanup jobs and indexes documented in the baseline.

## Delete after wiring

- [ ] Delete `SmartFarmUserDetailsService` if no AuthenticationManager flow remains.
- [ ] Delete `SuperAdminWildcardGrantRunner`.
- [ ] Delete old flat refresh entity/repository after session implementation is active.
- [ ] Delete direct repository role assignment paths.
- [ ] Delete obsolete README claims and REST admin documentation.

## Release gate

- [ ] Fresh PostgreSQL database starts with Flyway + Hibernate validate.
- [ ] Every gRPC method has a method policy.
- [ ] Every token grant has claim-contract tests.
- [ ] Bootstrap concurrency test passes.
- [ ] Refresh replay/concurrency tests pass.
- [ ] No secret or token appears in logs.
- [ ] No transport adapter signs tokens or mutates business repositories directly.
