# Troubleshooting — SmartFarm Platform

## "Unable to determine Dialect without JDBC metadata" (Hibernate boot fail)
**Nguyên nhân**: service boot không có profile → datasource (chỉ định nghĩa trong profile) không nạp.
**Fix (đã áp)**: base `application.yml` dùng `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}` + plugin pin `dev`. Nếu vẫn gặp: chạy explicit `SPRING_PROFILES_ACTIVE=dev` hoặc `-Dspring-boot.run.profiles=dev`.

## Service không boot: `jwt` bind null
**Nguyên nhân**: dùng flat key `smartfarm.security.issuer` thay vì nested `smartfarm.security.jwt.issuer` (ISSUE-01/CFG-01).
**Fix**: dùng nested form (xem [configuration.md](configuration.md)).

## Event identity không tới browser (WS)
**Trạng thái**: EVT-01/02 đã resolved và live-verified. Nếu tái diễn, kiểm tra HMAC verification, topic filter và protobuf `DomainEvent`.

## Health service không gọi được từ gateway
**Trạng thái**: GW-01 đã resolved. Kiểm tra `HEALTH_GRPC_HOST`, `HEALTH_GRPC_PORT=9097`, service token audience và deadline cấu hình Gateway.

## MQTT không nhận event
Kiểm tra `SMARTFARM_MQTT_EVENTS_ENABLED` (default `false` ở gateway) và broker `tcp://localhost:1883` chạy (`docker compose up -d mqtt`).

← [Configuration](configuration.md) · [Unresolved Items](../audit/unresolved-items.md)
