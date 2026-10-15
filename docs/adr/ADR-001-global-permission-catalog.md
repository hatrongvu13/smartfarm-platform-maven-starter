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
- Deprecated tenant mutation RPCs fail with `FAILED_PRECONDITION`; tenant-scoped
  application mutation APIs have been removed.
- A global permission mutation must increment tenant authorization versions for
  every affected tenant in the same transaction.

## Consequences

- Platform administration becomes the only permission-definition writer.
- Tenant role management remains tenant scoped.
- Permission-reference validation uses the global catalog.
- Contract, schema, and application API guard tests prevent the hybrid model
  from returning.
