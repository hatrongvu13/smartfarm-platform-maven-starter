# 08 — Script Register (Phase 7)

> Inventory + classification of scripts and build/infra helpers, from CHUNK-13.
> Classes: **KEEP / UPDATE / MERGE / REPLACE / REMOVE**. No script was deleted in this
> pass; the one change made is the `compose.yaml` healthcheck fix (RISK-CFG-01).

## Classification

| Path | Class | Note / action |
|---|---|---|
| `scripts/bootstrap.sh`, `scripts/doctor.sh`, `scripts/build-all.sh` | KEEP | Core dev entry points; take project root / MAVEN_HOME via env. Add a one-line usage header where missing (UPDATE-doc, non-blocking). |
| `scripts/release/v1/00..60-*.sh`, `_lib.sh`, `run-all.sh` | KEEP | Structured V1 release validation suite — the real integration path. Keep. |
| `scripts/release/production/validate-order-release.sh` | KEEP | Prod order release validation. Keep. |
| `scripts/livestock-lifecycle-test.sh`, `scripts/livestock-registry-test.sh` | KEEP | Livestock integration harnesses (livestock has no JUnit; these are its verification path). Keep until JUnit coverage lands. |
| `scripts/phase2-curl-test.sh`, `scripts/phase3-saga-curl-test.sh` | UPDATE | Phase curl harnesses; carry Vietnamese inline notes + a dev secret default (`GATEWAY_SERVICE_SECRET=dev-gateway-service-secret`). Keep as dev aids but document they are dev-only and env-overridable; do not treat as the only integration path. |
| `scripts/dev/curl-06-cancel-completed.sh`, `curl-07-multiline.sh`, `rebuild-restart.sh` | MERGE (candidate) | Ad-hoc dev curls / rebuild loop; hard-code `localhost:<port>` (env-overridable). Candidate to fold into the release suite's `_lib.sh` conventions. Low priority. |

## Rule conformance (CHUNK-13 + re-check)

- **No developer-absolute paths / committed real credentials** found in scripts — the
  secrets present are dev placeholders (`dev-*-secret`, `replace-with-a-long-random-password`),
  env-overridable in prod. (FACT)
- Scripts accept project root / MAVEN_HOME via env (FACT) — honours the "no hard-coded
  path" rule.
- Several scripts carry Vietnamese inline comments — harmless; left as-is.

## Change made this pass

- **RISK-CFG-01 FIXED** — `compose.yaml` postgres healthcheck probed `-U smartfarm`
  while the container superuser is `postgres`. Changed to `pg_isready -U postgres -d
  smartfarm` so the healthcheck matches the configured user/db. (dev infra; config only)

## Not removed

No script targets a deleted file/module (nothing was deleted in this cleanup — the
simulator was quarantined, not removed; `OrderSagaOrchestrator` was removed in a prior
engagement and no script references it). So no REMOVE candidates under the Phase 7 rule.
