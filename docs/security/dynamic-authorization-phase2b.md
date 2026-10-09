# Dynamic Authorization Phase 2B

Phase 2B connects effective subject authorization mutations to the Phase 1 version and
transactional outbox foundation.

Covered mutations:

- role assignment and revocation;
- farm-scope grant and revocation.

Each effective mutation increments the subject authorization version and records
`identity.authorization.invalidated` in the same transaction. Idempotent operations do not
produce a new version or invalidation event.

Compatibility overloads and existing constructors remain available. Spring injects the
`AuthorizationMutationCoordinator` through an optional setter so characterization tests that
construct services directly remain source-compatible.
