# Repository scripts

> Inventory reconciled 2026-10-15 against the files currently present under `scripts/`. The restored shell test runner contains health and smoke checks only; it does not include the REST/GraphQL suites described by older revisions of this file.

## Available scripts

| Script | Purpose |
|---|---|
| `doctor.sh` | Check Java/Maven and confirm every reactor module declared in the root POM has a `pom.xml`. |
| `build-all.sh` | Run `mvn -B clean verify` from the repository root; forwards command-line arguments to Maven. |
| `bootstrap.sh` | Check Java/Maven and run `mvn -B clean install`; `--skip-build` checks tools only. |
| `run-all.sh` | Run the available `health` and `smoke` HTTP checks, or one phase using `ONLY=health` / `ONLY=smoke`. |
| `health/test-health.sh` | Probe gateway health; direct service probes run only when the corresponding `*_HEALTH_URL` variable is set. |
| `smoke/smoke-test.sh` | Probe gateway health, GraphQL availability, optional OpenAPI docs, and `/api/v1/me` if configured credentials can authenticate. It performs no business-data mutation. |
| `config-forward.sh` | Forward the listed local TCP ports to a connected Android device using `adb`; this is an interactive/dev utility, not a platform test. |
| `dev/rebuild-restart.sh` | Dev helper; review its environment assumptions and target process before running. |
| `lib/*.sh` | Shared configuration, HTTP, authentication, assertions, and result helpers. |

No REST CRUD or GraphQL operation-suite scripts are present under `scripts/` in this checkout. Do not infer that a documented script exists from an older README.

## Requirements and configuration

The health/smoke runner requires Bash, `curl`, and `jq`. The build/doctor scripts require Java and Maven; `doctor.sh` also uses Python 3. Tests talk to an already-running gateway and do not start/stop the application.

Copy the example configuration if desired:

```bash
cp scripts/config/test.env.example scripts/config/test.env
```

`scripts/config/test.env` is ignored by Git. Never add real credentials or secrets to the example or repository. Defaults in `lib/common.sh` point to `http://localhost:8080`, with GraphQL at `/graphql`; `AUTH_BASE_URL` defaults to the gateway. `TEST_USERNAME` and `TEST_PASSWORD` enable the authenticated smoke probe. `TEST_TENANT_ID` is optional and is only needed to select among multiple active tenant memberships. `TEST_TOTP_CODE` can complete an MFA challenge; without it, authenticated smoke is skipped when MFA is required.

Optional `*_HEALTH_URL` values enable best-effort direct `/actuator/health` probes. The checked-in example provides local URLs for identity, livestock, inventory, finance, order, reporting, health, and readiness; omit a URL to skip that direct probe. The gateway probe remains required.

## Run

```bash
./scripts/doctor.sh
./scripts/build-all.sh
./scripts/run-all.sh
ONLY=health ./scripts/run-all.sh
ONLY=smoke ./scripts/run-all.sh
./scripts/health/test-health.sh
./scripts/smoke/smoke-test.sh
```

The shell runner currently contains only health and smoke. It does not implement REST CRUD coverage, GraphQL schema/introspection suites, destructive-test gates, or automatic service lifecycle management. The smoke phase sends a read-only `platformStatus` GraphQL request and optionally GETs `/api/v1/me`.

`run-all.sh` uses exit status `0` for passing available phases, `1` for one or more phase failures, and `3` when a required dependency is missing. A missing/unusable config file is non-fatal; exported variables and defaults are used. `health/test-health.sh` returns the shared assertion summary. Consult script source for the exact distinctions between failure and skip.

## Safety

- Keep `ALLOW_DESTRUCTIVE_TESTS` and data-mutating test claims out of the docs: there are no such restored test suites in the current tree.
- Scripts do not guarantee production safety; use only local/dev targets unless a release procedure explicitly authorizes another environment.
- Do not print bearer tokens, client secrets, passwords, or TOTP values. The HTTP helper masks the in-memory bearer token in its own logs, but callers remain responsible for not echoing secrets.
