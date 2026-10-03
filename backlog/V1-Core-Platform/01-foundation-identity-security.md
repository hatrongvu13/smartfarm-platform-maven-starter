# V1.1 Foundation / Identity / Security

## Checklist

- [ ] Chuẩn hóa TenantId, FarmId, UserId, ActorId, AggregateId.
- [ ] Chuẩn hóa UUID/ID policy.
- [ ] Chuẩn hóa CorrelationId, CausationId, EventId.
- [ ] Chuẩn hóa event envelope + schemaVersion.
- [ ] Chuẩn hóa UTC timestamp.
- [ ] Chuẩn hóa farm/zone scope.
- [ ] RBAC/permission.
- [ ] Service-to-service authentication.
- [ ] Audit actor.
- [ ] Idempotency key cho command.
- [ ] Outbox/inbox pattern.
- [ ] Không cross-service database access.

## Test

- [ ] Tenant isolation.
- [ ] Farm isolation.
- [ ] Unauthorized access.
- [ ] Duplicate command.
- [ ] Duplicate event.
- [ ] Event replay.
- [ ] Audit completeness.
