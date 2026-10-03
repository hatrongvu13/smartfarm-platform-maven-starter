# 11 — Verification Result (Phase 10)

> Authoritative verification of the approved Wave A–D cleanup. Build run with
> `MAVEN_HOME=/Users/jaxmac/sdk/apache-maven-3.9.16` (JDK17).

## Build verification — `mvn clean test`

**BUILD SUCCESS** (exit 0). Full reactor, every module green, 0 failures / 0 errors.

| Module | Tests | Note |
|---|---|---|
| common-kernel | 13 | unchanged |
| security | 27 | **MQTT verifier 8 unchanged — no security downgrade** |
| messaging | 12 | unchanged (ISSUE-04/05 infra) |
| health | 3 | `HealthOutboxRelayTest` green after Flyway (UPD-03) |
| **inventory** | **7** | **was 0 — new `ReservationEntityTest` + `BalanceEntityTest` (UPD-02)** |
| **identity** | **55** | incl. new `RoleManagementDelegationGuardTest` 4 (UPD-ID-01/02) |
| order | 22 | unchanged |
| finance / reporting / livestock / gateway / readiness / simulator | compile green | no test regressions |

- No dependency cycle introduced.
- No missing bean / unbound property (reporting resolver reuses existing `smartfarm.reporting.*`;
  no `smartfarm.messaging.mqtt.*` namespace invented — see `10`).
- The `ERROR` line from `GrpcExceptionMapperTest` is an intentional negative-path log, not a
  failure (that test reports 0 failures).

## Static verification

- **Flyway baselines** added for health (`V1__health_baseline.sql`) and reporting
  (`V1__reporting_baseline.sql`), authored to match the JPA entities exactly (table names,
  column lengths, named PK/unique/index constraints). Both services now run
  `ddl-auto: validate` in dev + prod, so a fresh DB is created cleanly by Flyway. *(Runtime
  migrate+validate against a live PostgreSQL is the one check not run here — no DB/broker was
  started per the environment rule; the DDL is entity-matched and the modules compile.)*
- finance prod `create-drop → validate` (UPD-01) — the data-loss line is gone.
- No script references a removed path (nothing was removed; simulator quarantined, not deleted).
- Docs: no current-architecture doc references a removed artifact (DOC-03 verified).
- **Graph regeneration**: deferred — the graph collector is a separate tool run; the graph
  (dated 2026-10-02) is known-stale re: `libs/smartfarm-messaging` and should be regenerated
  out-of-band. This does not affect the build.

## Tracked source changes (this engagement)

```
 M compose.yaml                                     (RISK-CFG-01 healthcheck)
 M services/smartfarm-finance-service/.../application-prod.yml      (UPD-01)
 M services/smartfarm-health-service/pom.xml + dev/prod yml         (UPD-03)
 ?? services/smartfarm-health-service/.../db/migration/V1__health_baseline.sql
 M services/smartfarm-reporting-service/pom.xml + dev/prod yml      (UPD-04)
 ?? services/smartfarm-reporting-service/.../db/migration/V1__reporting_baseline.sql
 M services/smartfarm-reporting-service/.../ReportingGrpcService.java (UPD-05)
 ?? services/smartfarm-reporting-service/.../download/*               (UPD-05 seam)
 M services/smartfarm-reporting-service/.../ReportDataProvider.java   (UPD-06)
 ?? services/smartfarm-inventory-service/src/test/**                  (UPD-02)
 ?? services/smartfarm-identity-service/.../RoleManagementDelegationGuardTest.java (UPD-ID-01/02)
 M platform/smartfarm-farm-simulator/.../SimulationController.java + TaskEventObserver.java (SKEL-01)
 ?? docs/runbooks/prod-super-admin-bootstrap.md                       (UPD-ID-03 / BL-03)
 ?? docs/cleanup/** , backlog/SERVICE-OWNERSHIP.md (M)                (DOC-01/04/05, Phase 3/4/5/7/8/9)
```

## Not verified (stated honestly)
- Runtime migrate+validate and a live MQTT round-trip (needs DB + broker, not started).
- Graph regeneration (separate collector run).
