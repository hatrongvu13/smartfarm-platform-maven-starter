# Outstanding Issues — need human confirmation before code changes

> Created by the audit+standardization pass **2026-10-07** (session: comprehensive audit).
> These issues were **reviewed and deliberately left UNCHANGED** because each is LARGE,
> behaviour-changing, or depends on infrastructure (broker ACL/TLS, prod topology) that a
> human must confirm. Code was **not** touched for any item below. Small/safe issues from the
> same pass were fixed and are recorded in [`unresolved-items.md`](../unresolved-items.md).
>
> Companion to the historical [`04-issue-register.md`](04-issue-register.md) (original audit snapshot)
> and the live [`unresolved-items.md`](../unresolved-items.md) (fix-pass log). This doc is the
> single list of what remains OPEN and WHY it is parked.

---

## ISSUE-11 — gRPC JWT does not enforce per-service audience  (MEDIUM, SECURITY)

**What.** The servlet resource-server path validates the token audience
(`SmartFarmJwtValidator` checks `jwt.getAudience()` against the service's configured
`smartfarm.security.jwt.audiences`). The **gRPC** path does not: `JwtServerInterceptor`
authenticates via `TokenVerifier` → `JwtTokenVerifier`, which only *decodes* the token and
captures its audiences into `SecurityIdentity.audiences()` — but nothing ever checks that set
against the receiving service's own identity. A per-service token minted for audience
`smartfarm-livestock` is therefore accepted by, say, the finance gRPC server.

**Why it needs confirmation (LARGE / behaviour-changing).** Turning on audience enforcement in
the interceptor can **reject currently-working inter-service calls** if any caller mints a token
with the wrong (or a shared) audience. The gateway's `ServiceTokenClient.tokenFor(audience, ...)`
already mints per-target-audience tokens, so in principle callers are correct — but that must be
verified across every caller (order↔inventory↔finance, gateway→all) before flipping it on, or a
mis-minted audience becomes a hard outage. Fail-closed security change on a live call graph.

**Suggested approach (not applied).**
1. Add an expected-audience to the gRPC side — e.g. `smartfarm.security.grpc.audience` on
   `SmartFarmSecurityProperties.Grpc`, defaulting to the service's own
   `smartfarm.security.jwt.audiences` when unset.
2. In `JwtServerInterceptor` (or a small dedicated check), after `verifier.verify(...)` succeeds,
   reject with `PERMISSION_DENIED` when `!identity.hasAudience(selfAudience)` — gated behind a
   feature flag (`smartfarm.security.grpc.audience-check-enabled`, default **false**) so it can be
   rolled out observe-then-enforce, exactly like the MQTT HMAC rollout.
3. Verify every service-token caller mints the correct target audience BEFORE enabling, with the
   flag off + an audit log of would-be rejects first.

**Blast radius.** `libs/smartfarm-security` (interceptor + properties) + every service that runs a
gRPC server. Must be verified live across the whole call graph.

---

## ISSUE-10 — Order MQTT publisher uses `cleanSession=true` + random client-id  (MEDIUM, RELIABILITY)

**What.** `OrderMqttPublisher` connects with `clientId = "smartfarm-order-" + UUID.randomUUID()`
and `options.setCleanSession(true)`, so there is no persistent publisher-side session. (The
*consumer* durable-session side is already handled centrally by the new
`libs/smartfarm-messaging` `MqttConsumerSettings`, which enforces the FILE-persistence + stable
client-id invariants — ISSUE-05.)

**Why it needs confirmation (depends on broker hardening).** The register's own completion
condition is *"stable client-id + `cleanSession=false` (**requires broker ACL**)"*. A stable
client-id with a durable session only pays off against a broker that authenticates clients and
scopes sessions per identity. The dev broker is `allow_anonymous true` with no ACL/TLS, and broker
ACL + mTLS were deliberately left as **documented sample config only** (see
[`docs/security/mqtt-acl-mtls.md`](../security/mqtt-acl-mtls.md), per user direction). Changing the
publisher session model in isolation, against an open broker, buys little and risks client-id
collisions on reconnect. Couple it with the ISSUE-02 broker-hardening work.

**Suggested approach (not applied).** When broker ACL+TLS is enabled: give the publisher a stable,
per-instance client-id (e.g. `smartfarm-order-${instanceId}`), set `cleanSession=false`, and
confirm the broker scopes the session to the authenticated identity. Roll out with the broker
hardening, not before.

**Blast radius.** `services/smartfarm-order-service` publisher + broker configuration (the 8
env/compose files carrying live secrets are **out of scope** for this session).

---

## ISSUE-13 — Fixed gRPC deadlines in dev facades; no circuit-breaker  (MEDIUM, RESILIENCE)

**What.** Two parts, different sizes:
- *Deadline externalization (SMALL, but parked):* the gateway's **business** REST paths already
  externalize the deadline (`OrderRestController` / `InventoryDevSetupController` take
  `@Value("${smartfarm.gateway.grpc.<svc>-deadline:5s}")`). The four **dev-only** facades
  (`LivestockDevController`, `LivestockRegistryController`, `ReportingDevController`,
  `HealthDevController`) still hardcode `withDeadlineAfter(3|5, SECONDS)`. Externalizing those is
  low-risk — but they are `@Profile("dev & !prod")` conveniences, so it was left unchanged this
  pass to keep the diff minimal during a verification-focused session. Easy follow-up if desired.
- *Circuit-breaker (LARGE):* there is no circuit-breaker / bulkhead around downstream gRPC calls.
  Adding one (Resilience4j or similar) is a new dependency + cross-cutting resilience policy that
  needs a design decision (per-service thresholds, fallback behaviour).

**Suggested approach (not applied).** (1) For dev facades, inject
`@Value("${smartfarm.gateway.grpc.<svc>-deadline:5s}") Duration` like the business controllers.
(2) For the circuit-breaker, decide on Resilience4j vs manual, define thresholds per downstream,
and wrap `GatewayGrpcChannelFactory` call sites. Confirm the library choice first.

**Blast radius.** (1) 4 gateway dev controllers. (2) gateway + a new resilience dependency.

---

## ISSUE-15 — `ddl-auto` inconsistent across profiles  (LOW, CONFIG)

**What.** `dev` uses `ddl-auto: update`, `prod` uses `validate` → risk of dev schema drift vs the
Flyway-managed prod schema.

**Why it needs confirmation (touches env config + migration policy).** Standardizing on
"Flyway-only, `ddl-auto: validate` everywhere" is the right end-state but changes how dev schemas
are created and could break local dev DBs that currently rely on auto-`update`. It also touches
per-service `application-dev.yml` files. Needs a team decision on the dev migration workflow
(there is already a [`docs/refactor/DEV-DB-WORKFLOW.md`](../refactor/DEV-DB-WORKFLOW.md)).

**Suggested approach (not applied).** Move all schema creation to Flyway, set `ddl-auto: validate`
in every profile, and document the `flyway:migrate` dev bootstrap step. Confirm the dev workflow
change with the team first.

---

## ISSUE-16 — Default dev passwords/secrets in `application-dev.yml`  (LOW, SECURITY)

**What.** `application-dev.yml` files carry default postgres/root passwords and a shared dev MQTT
HMAC secret.

**Why it is PARKED (hard constraint).** These live in the env/compose files that carry **live
Railway secrets**, which this session is **forbidden to touch**. They are also already clearly
scoped dev-only. Any change here is an operator action.

**Suggested approach (not applied).** Externalize via environment variables and keep the files
holding only non-secret dev defaults; document the required env vars. **Operator-owned** — not an
agent change.

---

## GW-02 — Finance has no production path through the gateway  (MEDIUM, COMPLETENESS)

**What.** Finance is reachable only through two `@Profile("dev & !prod")` GraphQL queries
(`FarmGraphQlController`); there is no production REST/GraphQL/gRPC-client exposure wired in the
gateway (contrast GW-01/health, now wired dev-side this session).

**Why it needs confirmation (LARGE / product decision).** Whether finance should be exposed at the
edge at all — and if so, which operations and under which scopes — is a product/security decision,
not a mechanical fix. Finance is a sensitive domain.

**Suggested approach (not applied).** Decide the finance edge contract (which queries/mutations,
which scopes, prod vs dev), then wire a prod controller + gRPC client config mirroring the
reporting/health pattern. Confirm scope with product first.

---

## Reviewed and confirmed NOT actionable this pass

- **ISSUE-07 (duplicate gRPC security auto-config)** — **re-verified FALSE POSITIVE.** The security
  lib has three *distinct* concerns, not duplicates: `SmartFarmSecurityPropertiesAutoConfiguration`
  (properties), `SmartFarmMqttSecurityAutoConfiguration` (MQTT), and
  `grpc/autoconfigure/SmartFarmGrpcSecurityAutoConfiguration` (gRPC interceptors). Identity's
  `IdentityGrpcSecurityConfiguration` is its own policy config, not a copy. Nothing to consolidate.
  The shared servlet permit-list lives in one place (`ServletResourceSecurity`), imported by
  services — not duplicated. No change.
- **ISSUE-06 (orphan `identity.command.result`)** — reclassified **by-design** in a prior pass
  (async command-ack to WS clients). No change.
