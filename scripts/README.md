# SmartFarm — Unified Test Scripts

Shell-based health, smoke, REST and GraphQL tests for the SmartFarm platform,
driven entirely from configuration and the real API surface. Compatible with
Linux and macOS Bash. Uses `curl` and `jq`.

> These tests exercise the running system over HTTP. They do **not** touch
> business logic, do **not** add JUnit/integration tests to the source tree, and
> do **not** start or stop services. Everything is read-only unless you opt in to
> destructive tests (see below).

## Layout

```
scripts/
├── README.md                      # this file
├── run-all.sh                     # top-level runner (health → smoke → REST → GraphQL)
├── config/
│   └── test.env.example           # copy to test.env and fill in (test.env is gitignored)
├── lib/
│   ├── common.sh                  # logging, deps, env loading, tally, exit codes
│   ├── http.sh                    # curl wrapper (status/body split, timeout, token masking)
│   ├── assertions.sh              # HTTP + GraphQL (data/errors) assertions
│   └── auth.sh                    # login (handles COMPLETED / MFA_REQUIRED)
├── health/test-health.sh          # gateway + per-service /actuator/health
├── smoke/smoke-test.sh            # fast "is the platform wired up?" check
├── rest/
│   ├── test-all-rest.sh
│   ├── identity/test-rest.sh      # /api/v1/auth/*, /api/v1/me
│   ├── livestock/test-rest.sh     # /api/v1/livestock/*
│   ├── inventory/test-rest.sh     # /api/v1/inventory/*
│   ├── order/test-rest.sh         # /api/v1/orders/*, /api/v1/order-sagas/*
│   └── reporting/test-rest.sh     # /api/v1/reports/*
├── graphql/
│   ├── test-all-graphql.sh
│   ├── test-introspection.sh
│   ├── test-schema.sh
│   └── gateway/
│       ├── queries/*.graphql      # one operation per file
│       ├── mutations/*.graphql
│       └── test-graphql.sh
└── legacy/                        # quarantine for superseded scripts (currently empty)
```

The **gateway (`:8080`) is the single ingress**: REST under `/api/v1/*`, GraphQL at
`/graphql`, auth proxied at `/api/v1/auth/*`. The suite targets the gateway by
default because that is the platform's primary access path. Domain services
(livestock, finance, reporting, health) are gRPC-internal and are reached through
the gateway, not tested directly over REST.

## Dependencies

- `bash` (4+ recommended; works on macOS `bash` 3.2 too)
- `curl`
- `jq`

`run-all.sh` checks these first and exits with code `3` if any is missing.

## Configuration

```bash
cp scripts/config/test.env.example scripts/config/test.env
# edit scripts/config/test.env
```

`test.env` is gitignored — **never commit real credentials**. All variables:

| Variable | Purpose | Default |
|---|---|---|
| `API_GATEWAY_URL` | Gateway ingress base | `http://localhost:8080` |
| `GRAPHQL_URL` | GraphQL endpoint | `$API_GATEWAY_URL/graphql` |
| `AUTH_BASE_URL` | Auth base (proxied via gateway) | `$API_GATEWAY_URL` |
| `TEST_TENANT_ID` / `TEST_USERNAME` / `TEST_PASSWORD` | Login (`{tenantId,email,password}`) | *(blank → auth tests skipped)* |
| `TEST_TOTP_CODE` | Completes an `MFA_REQUIRED` login | *(blank → MFA accounts skipped)* |
| `TEST_FARM_ID` | Farm id for farm-scoped ops | `farm-1` |
| `TEST_ITEM_ID` / `TEST_WAREHOUSE_ID` / `TEST_BATCH_ID` | Ids for deeper probes | *(blank → those steps skip)* |
| `REQUEST_TIMEOUT_SECONDS` | Per-request curl timeout | `30` |
| `GRAPHQL_INTROSPECTION_ENABLED` | `false` → introspection-blocked is expected | `true` |
| `ALLOW_DESTRUCTIVE_TESTS` | `true` → run data-mutating tests | `false` |
| `*_HEALTH_URL` | Direct per-service health probes | per service port |

## Running

```bash
# Everything (health → smoke → REST → GraphQL)
scripts/run-all.sh

# A specific env file
scripts/run-all.sh /path/to/custom.env

# One phase only
ONLY=health  scripts/run-all.sh
ONLY=smoke   scripts/run-all.sh
ONLY=rest    scripts/run-all.sh
ONLY=graphql scripts/run-all.sh
```

Run a single sub-suite or test directly (each is self-contained):

```bash
scripts/health/test-health.sh
scripts/rest/test-all-rest.sh
scripts/rest/order/test-rest.sh
scripts/graphql/test-all-graphql.sh
scripts/graphql/gateway/test-graphql.sh
```

### REST vs GraphQL only

```bash
ONLY=rest    scripts/run-all.sh      # REST only
ONLY=graphql scripts/run-all.sh      # GraphQL only (introspection + schema + ops)
```

### Enabling destructive tests

Data-mutating tests (create order draft, register animal, create inventory item,
GraphQL mutations) are **off by default**. Enable only against a disposable test
tenant:

```bash
ALLOW_DESTRUCTIVE_TESTS=true \
TEST_ITEM_ID=item-1 TEST_WAREHOUSE_ID=wh-A \
scripts/run-all.sh
```

Created data uses a unique identifier (`idempotencyKey` / unique `tagCode` / unique
`itemId`) per run; draft orders are deleted again as cleanup. Entities with no
delete endpoint (animals, items) are isolated by their unique id and left in the
test tenant.

## Reading results & exit codes

Each sub-suite prints a tally:

```
---- graphql-gateway ----
  total=9  pass=8  fail=1  skip=0
  failed:
    - dashboard (GraphQL errors present)
```

`run-all.sh` prints a final `RESULT` block and exits:

| Exit code | Meaning |
|---|---|
| `0` | every executed test passed |
| `1` | one or more tests failed |
| `2` | usage / missing config |
| `3` | a required dependency is missing |
| `4` | precondition failed (e.g. gateway unreachable) |

Skipped tests (service down, no creds, destructive gate off) do **not** fail the
suite — they are reported as `SKIP`.

**GraphQL assertions check the full envelope** — HTTP 200 **and** valid JSON **and**
`.data` present **and** no `.errors`. The gateway can return HTTP 200 with an
`errors` array for a failed operation, so status alone is never trusted.

## Adding a test

**New GraphQL operation:** drop a `.graphql` file in `graphql/gateway/queries/`
(or `mutations/`), then add one `gql_file "<name>" "$QDIR/<file>.graphql" "<vars-json>"`
line in `graphql/gateway/test-graphql.sh`.

**New REST test:** add a `rest/<service>/test-rest.sh` that sources the four libs,
calls `load_env`, uses `http_get/http_post/...` + the `assert_*` helpers, ends with
`summary "<label>"`, then list it in `rest/test-all-rest.sh`.

Conventions: `set -o errexit -o nounset -o pipefail`; assertions never abort the
file (they record pass/fail and `summary` sets the exit code); never print tokens
or secrets (the libs mask them automatically).

## Tests that cannot run without a live platform

Health, smoke, REST and GraphQL **operation** tests require the gateway (and the
relevant services) to be running — the suite reports them as failures/skips when
the gateway is unreachable, it does not fabricate passes. To bring the platform up
see the repository `README.md` and the existing `scripts/release/v1/` suite.

## Relationship to the existing `scripts/release/v1/` suite

This unified suite sits **alongside** the existing `scripts/release/v1/*` release
validation suite and the `scripts/*-test.sh` dev harnesses — it does not replace
or remove them. See the "Script classification" note below.

## Script classification (existing scripts review)

Reviewed against current source + `docs/cleanup/08-script-register.md`:

| Scripts | Decision |
|---|---|
| `release/v1/*`, `release/production/validate-order-release.sh` | **KEEP** — the real release validation path |
| `bootstrap.sh`, `doctor.sh`, `build-all.sh`, `config-forward.sh` | **KEEP** — build/env entry points |
| `livestock-*-test.sh`, `phase2/phase3-curl-test.sh`, `dev/curl-*`, `dev/rebuild-restart.sh` | **KEEP in place** — active dev harnesses; dev-only secret *placeholders* are env-overridable |
| *(none)* | **REMOVE / move to legacy** — no script is unreferenced, duplicated, or targets a removed endpoint |

No existing script was deleted or moved. `legacy/` is an empty convention location
for future demotions (see `legacy/README.md`).
