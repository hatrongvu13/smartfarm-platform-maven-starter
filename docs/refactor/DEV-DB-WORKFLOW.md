# DEV / TEST / PROD Database Strategy & Dev Reset Workflow

> Refactor §4/§5/§17/§18/§20. Defines the schema-management strategy per profile and the
> fast dev-reset loop. The strategy is now **consistent across all 7 persistent services**
> (identity, order, inventory, livestock, health, finance, reporting).

## Strategy per profile

| Profile | Schema owner | `ddl-auto` | Flyway | Rationale |
|---|---|---|---|---|
| **dev** | Hibernate | `update` | **disabled** | Fast iteration on a disposable local DB. No migration ceremony. |
| **test** | Hibernate (H2 in-mem) | `create-drop` | **disabled** | Isolated + reproducible; no dependency on a local PostgreSQL. In-memory DB discarded on JVM exit (`create-drop` is safe here). |
| **prod** | **Flyway** | `validate` | **enabled** | Controlled, explicit migrations. Hibernate only validates entities against the migrated schema. A restart can NEVER drop/recreate the schema. |

Enforced by `ProdSchemaSafetyTest` in every persistent service: the build fails if any
prod profile ever uses `create` / `create-drop` / `update`.

## Source of truth (§20)

- **dev**: Java entities → dev schema (Hibernate `update`).
- **test**: Java entities → H2 schema (Hibernate `create-drop`).
- **prod**: Flyway migrations → prod schema; Java entities **validated** against it.
  A drift between entities and the Flyway baseline surfaces at prod boot as a `validate`
  failure. (A live `validate` run against PostgreSQL is the authoritative drift check; the
  per-service `V1` baselines were authored to match the current entities.)

## Dev reset loop (§18)

Postgres runs via `compose.yaml` (service `postgres`, db `smartfarm`, user `postgres`).

```bash
# 1. stop + wipe the dev database volume
docker compose down -v

# 2. start a clean database
docker compose up -d postgres

# 3. run a service in dev (Hibernate recreates the schema from entities)
MAVEN_HOME=/Users/jaxmac/sdk/apache-maven-3.9.16
"$MAVEN_HOME/bin/mvn" -f services/smartfarm-identity-service/pom.xml spring-boot:run \
  -Dspring-boot.run.profiles=dev
```

Result: dev DB dropped → clean DB started → each service recreates its own tables from its
entities on startup (`ddl-auto: update`). No Flyway needed in dev.

## Prod migration evolution

Prod schema changes are made by adding a new `V<n>__*.sql` under each service's
`src/main/resources/db/migration/`. `baseline-on-migrate: true` lets Flyway adopt an
existing DB at baseline 0. Never edit an applied migration; add a new one.

## Flyway decision (explicit)

- **DEV Flyway: disabled** — fast iteration; Hibernate owns the schema.
- **TEST Flyway: disabled** — H2 in-memory, Hibernate `create-drop`, reproducible.
- **PROD Flyway: enabled** — the only controlled schema-evolution path; `ddl-auto: validate`.

Flyway migrations are **retained** for all persistent services (never deleted to simplify
dev). identity previously had Flyway enabled in prod but **no migrations** — fixed by
adding `V1__identity_baseline.sql`.
