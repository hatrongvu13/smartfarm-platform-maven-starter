# Observability — SmartFarm Platform

> Verified từ `observability/` @ HEAD `eaaa112`.

## Hiện có
- `observability/docker-compose.monitoring.yml` — Prometheus + Grafana.
- Prometheus: `prometheus/prometheus.yml`, alerts `prometheus/order-alerts.yml`.
- Grafana: dashboard `smartfarm-order-operations.json` + provisioning (datasource prometheus, dashboard order).
- Health endpoints: `/actuator/health` (+ liveness/readiness) mọi service.
- Correlation ID (`x-correlation-id`) xuyên gateway→service.
- Order: operational metrics + outbox health indicator.

## Phạm vi
**Chỉ order + gateway** có Prometheus scrape + Grafana dashboard + alerts.

## Thiếu (prod)
- Dashboard/alerts cho 5 service còn lại.
- Distributed tracing (không thấy OTel/Zipkin config).
- Cluster-wide metrics aggregation.
- Log aggregation (chỉ console/correlation hiện tại).

← [Deployment](deployment.md) · [Order SLO](ORDER-SLO.md)
