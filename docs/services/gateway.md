# Service: Gateway (`apps/smartfarm-gateway`)

> Verified @ HEAD (updated 2026-10-07). ← [System Overview](../architecture/system-overview.md)

## Trách nhiệm
Edge/ingress duy nhất của hệ thống. Spring WebFlux reactive, :8080. Terminate client REST + GraphQL, dịch sang gRPC fan-out tới backend; proxy auth sang identity qua HTTP (WebClient); bridge MQTT domain events → WebSocket cho browser.

## Inbound interface
- **REST** `/api/v1/...` (auth proxy, `/api/v1/me` — THIN `{subject,tenantId}`, scope `farm:read`, profile qua GraphQL `me`; orders, order-sagas; livestock/inventory/reporting/health là **dev-only**).
- **GraphQL** `/graphql` (19 query + 19 mutation).
- **WebSocket** `/ws/**` (event stream, permitAll).

## Outbound dependency
- gRPC → identity (:9092), order (:9095), inventory (:9093), reporting (:9096), finance (:9094), livestock (:9091), health (:9097, dev REST facade — GW-01).
- REST → identity (:8092) cho auth.
- MQTT sub `smartfarm/+/+/domain/#` (default `enabled=false`).

## Auth & authz
- JWT RS256 validate (issuer/audience=`smartfarm-gateway`/JWKS). permitAll: `/actuator/health*`, docs, `/ws/**`, 5 auth endpoint. Còn lại `authenticated()`. Order/saga có `@PreAuthorize` scope.
- Service token per audience khi gọi downstream gRPC.

## Configuration chính
`SMARTFARM_JWT_AUDIENCE`, `IDENTITY_ISSUER`, `IDENTITY_JWK_SET_URI`, `*_GRPC_HOST/PORT`, `SMARTFARM_MQTT_URL`, `SMARTFARM_MQTT_EVENTS_ENABLED`, `GATEWAY_SERVICE_CLIENT_SECRET`.

## Chạy local
`mvn -f apps/smartfarm-gateway/pom.xml spring-boot:run` (profile `dev` tự nhận). Cần identity :8092 chạy trước.

## Health / observability
`/actuator/health` (liveness/readiness). Prometheus/Grafana có cho gateway (observability/).

## Known limitations
- health-service: đã nối gateway nhưng mới chỉ **dev REST facade** (GW-01 fixed; prod route chưa có).
- finance chỉ 2 dev-only GraphQL (GW-02).
- Duplicate order read surface (`order`/`orders` vs v2).
- Dev-only facade cho livestock/inventory/reporting/health — chưa có đường prod.

## TODO còn hiệu lực
- [ ] Prod REST/GraphQL cho livestock/inventory/reporting/health (EVT/GW).

> Bug đã sửa (GW-01 pass): `finance.grpc-port` trong gateway config từng là `9097` (sai — đó là port health); finance thực chạy gRPC `9094`. Đã sửa về `9094`.

[Gateway Mapping](../architecture/gateway-mapping.md) · [Request Flows](../architecture/request-flows.md)
