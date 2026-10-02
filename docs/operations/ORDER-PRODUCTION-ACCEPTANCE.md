# Order production acceptance checklist

## Purpose

This checklist is the release gate for SmartFarm Gateway and Order production artifacts. It validates deployment readiness without executing business flows or changing production data.

## Required command

```bash
./scripts/release/production/validate-order-release.sh strict
```

Release acceptance requires `FAIL: 0`. A `SKIP` must be reviewed and supported by equivalent external evidence.

## Build evidence

- Maven packages Gateway, Order, and required reactor dependencies with tests skipped according to the current phase policy.
- Order and Gateway container images build successfully.
- Runtime images run as UID/GID 1001.
- Images contain readiness healthchecks.
- OCI revision matches the release commit.
- CI publishes SBOM and provenance.

## Configuration and secrets

- `SPRING_PROFILES_ACTIVE=prod` is explicit.
- PostgreSQL settings are supplied externally.
- Flyway is enabled, validation is enabled, and clean is disabled.
- Hibernate uses `ddl-auto=validate`.
- Gateway and Order service-client secrets are supplied through a secret store.
- No literal passwords, tokens, credentials, or Kubernetes Secret values are committed.
- Swagger UI, OpenAPI docs, and GraphiQL are disabled in production.

## Deployment

- Images are pinned by `@sha256` digest. Tags and placeholders are not accepted.
- Kubernetes client dry-run succeeds.
- Rolling update uses `maxUnavailable: 0` and `maxSurge: 1`.
- Startup, readiness, and liveness probes are configured.
- Pod security context is non-root with read-only root filesystem and dropped capabilities.
- Termination grace period exceeds the Spring shutdown timeout.
- PodDisruptionBudget exists for Gateway and Order.

## Runtime acceptance

- Gateway liveness and readiness return HTTP 200 with `status=UP`.
- Order liveness and readiness return HTTP 200 with `status=UP`.
- Gateway and Order Prometheus endpoints respond successfully.
- Swagger, OpenAPI, and GraphiQL are unavailable externally.
- The following metrics exist and equal zero at acceptance time:

```text
smartfarm_order_saga_stale_claims
smartfarm_order_outbox_stale_claims
smartfarm_order_projection_gaps_stale_claims
smartfarm_order_outbox_dead
```

Operational backlog may exist, but its age must be decreasing and remain inside the approved SLO thresholds.

## Rollout verification

```bash
kubectl rollout status deployment/smartfarm-order --timeout=5m
kubectl rollout status deployment/smartfarm-gateway --timeout=5m
kubectl get pods -l app=smartfarm-order
kubectl get pods -l app=smartfarm-gateway
```

After rollout, run runtime validation again and observe backlog/stale-claim metrics for at least ten minutes.

## Rollback verification

```bash
kubectl rollout undo deployment/smartfarm-order
kubectl rollout undo deployment/smartfarm-gateway
kubectl rollout status deployment/smartfarm-order --timeout=5m
kubectl rollout status deployment/smartfarm-gateway --timeout=5m
./scripts/release/production/validate-order-release.sh runtime
```

Rollback is accepted only when readiness is restored, stale claims are zero, no new dead outbox event appears, and backlog age begins decreasing.

## Evidence retention

Retain the generated file:

```text
build/order-production-validation-report.md
```

Also retain Maven/Docker logs, image digests, CI provenance, SBOM, rollout revision, approver, and release timestamp. Never copy secret values into the report.
