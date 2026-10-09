# Dynamic Authorization Phase 1 Foundation

This change introduces authoritative tenant and subject authorization versions in Identity.
It does not switch any service to ENFORCE mode.

## Added

- `sf_authorization_tenant_version`
- `sf_authorization_subject_version`
- `sf_permission_registration`
- pessimistically locked version repositories
- `AuthorizationVersionService`
- `AuthorizationInvalidationRecorder`, reusing `sf_identity_outbox`

## Required next integration

Every effective authorization mutation must perform these operations in one transaction:

1. mutate role, permission, membership, account or farm scope;
2. increment the relevant subject or tenant authorization version;
3. record `identity.authorization.invalidated` through `AuthorizationInvalidationRecorder`.

Subject mutations include role assignment/revocation, membership status, account enabled/locked
state, and farm-scope changes. Tenant mutations include role-permission and permission-catalog
changes that can affect multiple subjects.

The existing static scope checks remain authoritative until SHADOW comparison is implemented.
