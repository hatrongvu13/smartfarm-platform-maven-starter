# CHUNK-02 — services/smartfarm-identity

**Scope:** identity, authorization, multi-tenant, MFA, token, messaging.

**Graph manifest (graphslice --summary):**
- 1144 nodes · inbound 0 / outbound 1578
- annotations: {Service:93, Entity:49, Repository:33, Configuration:14, GrpcService:14}
- (inbound 0 is a static-graph artifact: callers reach identity over gRPC/MQTT at runtime)

**5-line summary**
1. Entry points: REST AuthController; 4 gRPC services (Directory, Credential, Administration, PlatformAuthorizationAdministration); MQTT command dispatcher + connection manager; scheduled outbox relay + inbox maintenance.
2. Persistence: rich JPA — tenant/membership, accounts/profiles, roles/permissions/role-permission/membership-role/membership-farm, MFA (challenge/authenticator/recovery-code), refresh tokens, outbox + MQTT inbox (49 @Entity, 33 @Repository). Hibernate-managed schema (no Flyway).
3. Security: AuthorizationService, FarmScopeService, TenantMembershipService, GrpcMethodPolicy; AuthService uses constant-time dummyHash to defeat user-enumeration timing (mitigation, not demo).
4. Reliability: idempotent MQTT inbox, outbox relay with shared DispatchFailureClassifier (ISSUE-09), GrpcExceptionMapper; 8 unit tests.
5. Verdict: PRODUCTION_IMPLEMENTATION. RISK: no Flyway versioned migrations (relies on ddl-auto).
