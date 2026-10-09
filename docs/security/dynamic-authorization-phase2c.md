# Dynamic Authorization Phase 2C

Phase 2C extends authorization invalidation to broader policy mutations.

## Covered

- Effective role-permission grant/revoke increments the tenant authorization version.
- A password failure that newly locks an account increments that subject's authorization version.
- Existing permission-catalog synchronization remains startup/bootstrap infrastructure. Catalog
  synchronization without a concrete tenant cannot emit a tenant-scoped invalidation safely.

Role-permission invalidation is tenant-wide because one role can affect many memberships. Account
lock invalidation is subject-scoped and only occurs on the transition into the locked state, not on
every failed login attempt.

## Deferred catalog rule

Runtime permission catalog administration must call
`AuthorizationMutationCoordinator.tenantChanged(...)` with the affected tenant. Global startup
catalog synchronization must complete before traffic is admitted or explicitly fan out to every
enabled tenant. It must not publish an invalidation with a fabricated tenant identifier.
