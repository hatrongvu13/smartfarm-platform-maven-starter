# Dynamic Authorization Phase 2A

Connects effective membership creation and status transitions to subject-version increment
and `identity.authorization.invalidated` recording in the same transaction.

No invalidation is emitted for an idempotent status transition. Remaining Phase 2 work covers
role, role-permission, farm-scope, account-state and permission-catalog mutations.
