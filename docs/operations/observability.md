# Observability — SmartFarm Platform

> Reconciled 2026-10-15 against the current checkout. The `observability/` configuration files are absent from this worktree, so the monitoring stack described in earlier revisions is not currently deployable from this repository state.

## Present in source
- Health endpoints: `/actuator/health` (+ liveness/readiness) mọi service.
- Correlation ID (`x-correlation-id`) xuyên gateway→service.
- Order: operational metrics + outbox health indicator.

## Phạm vi
Application metrics/health instrumentation exists, but no Prometheus/Grafana compose, scrape, alert, or dashboard configuration is present in the current checkout.

## Gaps
- Restore or recreate the monitoring configuration before claiming a runnable Prometheus/Grafana stack.
- Dashboard/alerts for all services.
- Distributed tracing (không thấy OTel/Zipkin config).
- Cluster-wide metrics aggregation.
- Log aggregation.

← [Deployment](deployment.md) · [Order SLO](ORDER-SLO.md)
