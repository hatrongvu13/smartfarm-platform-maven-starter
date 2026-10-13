# ADR-001: Global Platform-Owned Permission Catalog

- Status: Accepted
- Date: 2026-10-13
- Scope: SmartFarm Identity Service

## Context

The previous contract allowed tenant administrators to mutate permission rows
that were globally shared. A tenant-local mutation could therefore affect roles
or authorization decisions in other tenants while invalidating only one tenant.
This hybrid ownership model is unsafe.

## Decision

The permission vocabulary is global and owned exclusively by platform
administration.

- `sf_permission` has no `tenant_id` column.
- Permission codes are globally unique.
- Tenant administrators may create tenant roles and attach permissions from the
  global catalog, but may not create, update, or delete permission definitions.
- Global permission CRUD is exposed by
  `PlatformAuthorizationAdministrationService`.
- Existing tenant permission mutation RPCs are retained temporarily with the
  protobuf `deprecated` option for wire compatibility.
- Deprecated tenant mutation RPCs must be retired after consumers migrate.
- A global permission mutation must increment tenant authorization versions for
  every affected tenant, or use an equivalent global catalog version checked by
  downstream authorization enforcement.

## Consequences

- Platform administration becomes the only permission-definition writer.
- Tenant role management remains tenant scoped.
- Permission-reference validation uses the global catalog.
- Contract and schema guard tests prevent the hybrid model from returning.
- A later phase must implement global invalidation and disable deprecated tenant
  mutation methods at runtime.
