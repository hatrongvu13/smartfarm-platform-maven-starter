# Dynamic Authorization Phase 2D

Phase 2D completes the first authorization-mutation invalidation pass.

## Account state

Effective account enable and disable operations increment the subject authorization version and
record `identity.authorization.invalidated` in the same transaction. Idempotent state requests do
not emit a new version.

Account state is global while authorization versions are tenant-scoped. Therefore the mutation
must carry a concrete tenant identifier. Legacy overloads without tenant context remain compatible
but cannot emit a tenant-scoped invalidation safely.

## Tenant-aware permission catalog administration

`TenantPermissionCatalogAdministrationService` provides the transaction boundary for runtime
catalog mutations. A caller must provide:

- tenant identifier;
- authenticated actor identifier;
- correlation identifier when available;
- a stable reason code;
- the catalog mutation callback.

The mutation and tenant-version invalidation commit or roll back together.

Startup permission synchronization remains separate. It must finish before readiness or explicitly
fan out changes to every enabled tenant. It must never invent a tenant identifier merely to emit an
invalidation event.
