# CHUNK-13 — scripts / config / docs

**Scope:** scripts/, compose.yaml, Dockerfile, infra/, deploy/, observability/, and
application*.yml across modules.

**scripts/ inventory:**
- Top-level shell: bootstrap.sh, doctor.sh, build-all.sh; livestock-lifecycle-test.sh, livestock-registry-test.sh; phase2-curl-test.sh, phase3-saga-curl-test.sh.
- scripts/dev/: curl-06-cancel-completed.sh, curl-07-multiline.sh, rebuild-restart.sh (ad-hoc dev).
- scripts/release/v1/: 00-preflight → 10-auth-rbac → 20-livestock-flow → 30-order-saga → 40-reporting → 50-reporting-render → 60-graphql, _lib.sh, run-all.sh (structured V1 release suite).
- scripts/release/production/validate-order-release.sh.

**Infra/config top files:** compose.yaml (postgres/mqtt/redis), Dockerfile
(hardened multi-stage, non-root uid 1001, OOM flags, healthcheck), infra/mosquitto.conf,
.env.production.example (names only), deploy/kubernetes/order-gateway.yaml+README,
observability/ (prometheus order-alerts+prometheus.yml, grafana order dashboard +
provisioning, docker-compose.monitoring.yml).

**Smells (worst first):**
1. **BLOCKER (FACT)**: finance `application-prod.yml` ddl-auto=create-drop → prod schema dropped+recreated every restart (data loss).
2. **RISK (FACT)**: reporting `application-prod.yml` has no ddl-auto override + zero Flyway → Hibernate schema generation in prod.
3. **RISK (FACT)**: infra/mosquitto.conf `allow_anonymous true` + unauthenticated ws:9001 — open dev broker; app-layer HMAC mitigates integrity only.
4. **RISK (FACT)**: compose.yaml postgres PASSWORD=root and healthcheck `pg_isready -U smartfarm` vs superuser `postgres` — latent user mismatch (dev-only).
5. **FACT (low)**: committed dev secrets (dev-order-service-secret, dev-reporting-service-secret, dev-gateway-service-secret) — env-overridable in prod; test passwords are the literal `replace-with-a-long-random-password`.
6. **FACT (hygiene)**: dev/phase curl harnesses hard-code localhost:<port> (env-overridable); several carry Vietnamese comments.

**UNKNOWN:** whether deploy/kubernetes covers all services or only order+gateway
(named for two of eight) — leave deploy-coverage to the orchestrator.
