# Runbook — Production Super-Admin Bootstrap

> Resolves cleanup item **UPD-ID-03** / blocker **BL-03**. The automatic
> `DevSuperAdminBootstrap` is `@Profile("dev & !prod")` **by design** — it seeds a
> known-password admin and must NEVER run in production. A production environment
> therefore needs an explicit, auditable first-admin procedure. This runbook is that
> procedure. It is a one-time operation per tenant.

## Why there is no automatic prod bootstrap

A prod deployment starts with an empty identity database and no SUPERADMIN. The
single-super-admin invariant is enforced at runtime: `RoleManagementService.assignRole`
and `DefaultTenantIdentityAdministrationService` both reject granting `SUPERADMIN` /
`PLATFORM_ADMIN` through the ordinary tenant-administration API
(`PLATFORM_ROLE_DELEGATION_NOT_ALLOWED`, locked by `RoleManagementDelegationGuardTest`).
So the first SUPERADMIN **cannot** be created through the normal API — that is the point.
It must be seeded out-of-band, once, by an operator with database access.

## Prerequisites

- Identity service deployed, Flyway migrations applied (schema present, no data).
- Direct DB access to the identity database (operator credential, not the app user).
- A strong, randomly generated password held only by the operator (never committed,
  never logged). The app hashes it; the plaintext is used once at creation and discarded.

## The entities to create (mirrors `DevSuperAdminBootstrap.run`)

Create, in order, within one transaction:

1. **Tenant** — `TenantEntity(id=UUID, code=<tenant-code>, name=<display>)`, enabled.
2. **Account** — via `AccountService.createAccount(CreateAccountCommand(email, password,
   displayName, …))` so the password is hashed with the production hasher. Do **not**
   insert a raw password hash by hand.
3. **Membership** — `TenantMembershipEntity(id=UUID, tenant, account)`, then `activate()`.
4. **Role** — `RoleEntity(id=UUID, tenant, code="SUPERADMIN", name="Super Administrator",
   system=true)` if absent.
5. **Binding** — `MembershipRoleEntity(membership, role, grantedBy=account.id)`.

## Recommended mechanism (choose one, document which you used)

**Option A — one-time bootstrap job (preferred).** Ship a `@Profile("bootstrap")`
`ApplicationRunner` that performs steps 1–5 from env-provided values
(`SMARTFARM_BOOTSTRAP_TENANT`, `_EMAIL`, `_PASSWORD`), then **refuses to run if a
SUPERADMIN already exists** (idempotent + safe to leave wired). Run the service once
with `--spring.profiles.active=prod,bootstrap`, confirm the log line, then redeploy
without the `bootstrap` profile. This reuses the exact dev logic behind a non-dev,
operator-gated profile.

**Option B — operator SQL + hash.** Generate the account via a short admin CLI that
calls `AccountService.createAccount` (so hashing matches), then insert the role +
binding rows. Heavier to get right (hash must match the app); only if A is not viable.

> Do NOT: enable the `dev` profile in prod; copy `DevSuperAdminBootstrap`'s default
> password; or hand-write a bcrypt hash whose cost/pepper may not match the app config.

## Post-bootstrap verification

- `SELECT count(*) FROM <role table> WHERE code='SUPERADMIN';` returns exactly 1 per tenant.
- The super admin can authenticate and is forced through **first-login TOTP enrollment**
  before any privileged operation (MfaRequirementService gates ADMIN/PLATFORM_ADMIN/
  SUPERADMIN).
- Attempting to grant a second SUPERADMIN via the API returns
  `PLATFORM_ROLE_DELEGATION_NOT_ALLOWED` (invariant intact).
- The bootstrap plaintext password is rotated/discarded; it appears in no log or config.

## Audit

Record who ran the bootstrap, when, and against which tenant. The account-creation and
role-binding emit identity integration events; confirm they landed in the outbox.
