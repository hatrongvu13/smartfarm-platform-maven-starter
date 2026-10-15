# Troubleshooting — SmartFarm Platform

## "Unable to determine Dialect without JDBC metadata" (Hibernate boot fail)
**Nguyên nhân**: service boot không có profile → datasource (chỉ định nghĩa trong profile) không nạp.
**Fix (đã áp)**: base `application.yml` dùng `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}` + plugin pin `dev`. Nếu vẫn gặp: chạy explicit `SPRING_PROFILES_ACTIVE=dev` hoặc `-Dspring-boot.run.profiles=dev`.

## Service không boot: `jwt` bind null
**Nguyên nhân**: dùng flat key `smartfarm.security.issuer` thay vì nested `smartfarm.security.jwt.issuer` (ISSUE-01/CFG-01).
**Fix**: dùng nested form (xem [configuration.md](configuration.md)).

## Event identity không tới browser (WS)
**Source status**: the identity publisher constructs a protobuf `DomainEvent` and the normalized topic. If the problem recurs, inspect deployed publisher configuration, broker topic filter, gateway subscriber, and HMAC key/verification settings; source implementation alone does not establish delivery.

## Health service không gọi được từ gateway
**Trạng thái**: GW-01 đã resolved. Kiểm tra `HEALTH_GRPC_HOST`, `HEALTH_GRPC_PORT=9097`, service token audience và deadline cấu hình Gateway.

## MQTT không nhận event
Kiểm tra `SMARTFARM_MQTT_EVENTS_ENABLED` (gateway configuration defaults to `true`), broker URL/credentials, and broker reachability (`docker compose up -d mqtt`). App-level HMAC defaults are profile-specific: enabled with a development key in dev and disabled by default in prod. Broker auth/ACL/TLS are separate from message signing.

← [Configuration](configuration.md) · [Identity backlog](../../IDENTITY_BACKLOG_UPDATED.md)
