# SmartFarm Platform — Bruno API Collection

A manual test collection for the SmartFarm platform, organized **one folder per
service** with ordered `NN - name.bru` requests. Built for the case where the shell
suite (`scripts/run-all.sh`) can't authenticate because the admin account is forced
to use TOTP — here you drive the **TOTP enrollment** by hand and keep testing.

Everything was verified against the source controllers (paths, request bodies,
required scopes). Where a request is DEV-only it says so.

## 1. Install & open

1. Install Bruno — https://www.usebruno.com (desktop app) or the CLI (`npm i -g @usebruno/cli`).
2. In Bruno: **File → Open Collection** → pick this `bruno/` folder.
3. Top-right environment selector → choose **local**.
4. Open the **local** environment and check the vars:
   - `gatewayUrl` = `http://localhost:8080` (single ingress: REST + GraphQL)
   - `identityUrl` = `http://localhost:8092` (identity service, direct — needed only for TOTP enrollment)
   - `tenantId`, `adminEmail`, `adminPassword` — pre-filled with the bootstrapped dev admin.
     `adminPassword` defaults to the YAML bootstrap value `replace-with-a-long-random-password`;
     if you started identity with a custom `IDENTITY_BOOTSTRAP_PASSWORD`, change it here.

> **Single ingress.** Everything — including TOTP enrollment — now goes through the gateway
> `{{gatewayUrl}}`. The gateway proxies all public auth endpoints (login, mfa/verify,
> **mfa/enrollment/begin**, **mfa/enrollment/confirm**, refresh, logout). The services bind
> loopback (`127.0.0.1`) by default and are NOT reachable from outside the host, so no client
> should ever call `{{identityUrl}}` directly. `identityUrl` is kept in the env only for
> low-level debugging on the same host.

## 2. Folder order

| Folder | What it covers | Needs a token? |
|---|---|---|
| `01 - identity` | login, **TOTP enrollment**, mfa-verify, whoami, refresh, logout | obtains it |
| `02 - gateway-graphql` | introspection, platformStatus, me | yes |
| `03 - inventory` | create item → receive stock (DEV) | yes |
| `04 - order` | create draft → submit → get → list → cancel | yes |
| `05 - livestock` | register animal, list, create task (DEV) | yes |
| `06 - reporting` | request export → poll job → download (DEV) | yes |

Run `01 - identity` first — it saves `accessToken` into the **local** environment, and
every other request reads `{{accessToken}}` as a Bearer token automatically.

## 3. First-time auth — bootstrap the super-admin, then TOTP (optional)

There is **no auto-created default admin**. Seed the first system super-admin once, then log in with
email + password.

**One-time bootstrap** (folder `01 - identity`):

1. **`00 - bootstrap-superadmin`** → on a fresh system returns **200 COMPLETED** with an `accessToken`
   (auto-saved). You are authenticated immediately — the token carries the wildcard scope `*`, so it
   passes every scope-gated endpoint. A second call returns **409** (bootstrap is closed). The identity
   service also prints an `otpauth://` TOTP enrollment URI to its **console** (never over HTTP) so you
   can enable MFA later.

**Normal login** (email + password only — no tenantId):

1. **`01 - login`** → `{email, password}`. The server resolves the tenant from the email:
   - **COMPLETED** → token saved, done.
   - **MFA_REQUIRED** → go to `05 - mfa-verify` with a 6-digit code.
   - **MFA_ENROLLMENT_REQUIRED** → the SUPERADMIN hasn't enrolled TOTP yet → `02 - mfa-enrollment-begin`
     → `03 - mfa-enrollment-confirm` (enroll once), which returns a token.
   - **TENANT_SELECTION_REQUIRED** → the email has several tenants (`.tenants[]`); resend `01 - login`
     with `"tenantId": "<id>"` added to the body to choose one.

**Enabling TOTP** (optional): `02 - mfa-enrollment-begin` → scan the `otpauthUri` (or
`oathtool --totp -b "<secret>"`, `brew install oath-toolkit`) → set env `totpCode` →
`03 - mfa-enrollment-confirm` (returns `recoveryCodes` — save them). Afterwards every login is
`MFA_REQUIRED` → `05 - mfa-verify`. Within the 15-min window, `07 - refresh token` rotates without a code.

Check your token any time with **`06 - whoami (me)`** — 401 means expired, re-auth.

## 4. End-to-end data walkthrough (order saga)

To exercise a real cross-service flow with data, run in this order after authenticating:

1. `03 - inventory / 01 - create item` → saves `itemId`.
2. `03 - inventory / 02 - receive stock` → stock on hand; saves `warehouseId`.
3. `04 - order / 01 - create draft order` → uses `{{itemId}}` + `{{warehouseId}}`; saves `orderId` + `orderVersion`.
4. `04 - order / 02 - submit draft order` → starts the saga (reserve stock → post ledger).
5. `04 - order / 03 - get order` → watch `status` settle (e.g. `ORDER_STATUS_CONFIRMED`).
6. `04 - order / 05 - cancel order` → teardown (compensation runs if stock was reserved).

Captured ids flow between requests via environment vars, so you rarely paste anything by
hand except the one-time setup and the rotating `totpCode`.

## 5. Scopes cheat-sheet (why a request might 403)

The token's authorities come from the user's roles. SUPERADMIN carries the wildcard `*`
(→ `SCOPE_*`), so it passes everything. A narrower user needs the matching scope:

| Area | Scope |
|---|---|
| GraphQL `platformStatus`, livestock reads | `farm:read` |
| orders read / write | `orders:read` / `orders:write` |
| inventory write | `inventory:write` |
| livestock tasks + animal register | `tasks:write` |
| reporting read / write | `report:read` / `report:write` |

## 6. Notes & gotchas

- **DEV-only endpoints**: inventory, reporting, and the livestock task/registry controllers
  are `@Profile("dev & !prod")`. They exist only when services run with the `dev` profile
  (the default per `spring.profiles.active=${SPRING_PROFILES_ACTIVE:dev}`).
- **`Idempotency-Key`** is required on write endpoints (order place/draft, inventory,
  livestock task, reporting). The requests auto-generate one with `{{$timestamp}}`.
- **Secrets**: this collection ships the *dev bootstrap* password only. Do not put a real
  password or a production token in `environments/local.bru` — add a gitignored
  `environments/*.local.bru` for anything sensitive.
- **CLI runs**: `cd bruno && bru run "01 - identity" --env local` runs a folder headless
  (the TOTP steps still need a human to supply `totpCode`, so enrollment is interactive).
