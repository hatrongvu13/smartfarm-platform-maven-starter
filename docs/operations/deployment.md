# Deployment — SmartFarm Platform

> Verified từ Dockerfile, `.github/workflows/build-push.yml`, `deploy/kubernetes/`, `compose.yaml` @ HEAD `eaaa112`.

## Container
- Root `Dockerfile` — hardened, non-root, parameterized (build 1 image/module theo arg).
- `.dockerignore` present.

## CI/CD
`.github/workflows/build-push.yml`: build + push **7 image GHCR** với SBOM + provenance. **Chưa có** fail-on-CVE scan gate.

## Kubernetes
`deploy/kubernetes/order-gateway.yaml` + README — **chỉ order + gateway** có manifest production-grade. **Thiếu** K8s cho 5 service còn lại + Postgres/Mosquitto/Redis. Không có Helm/IaC.

## Local infra (dev)
`compose.yaml`: postgres:17-alpine (healthcheck), eclipse-mosquitto:2 (`infra/mosquitto.conf`, `allow_anonymous true` ⚠️), redis:8-alpine.

## Production readiness gaps (xem runtime-readiness)
- MQTT broker: thêm auth/TLS/ACL (hiện anonymous).
- K8s: 5 service + stateful infra.
- Observability cluster-wide + tracing.
- `.env.production.example` → inject secret thật.

← [runtime-readiness](../07-runtime-readiness.md) · [observability](observability.md)
