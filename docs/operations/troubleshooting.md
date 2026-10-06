# Troubleshooting — SmartFarm Platform

## "Unable to determine Dialect without JDBC metadata" (Hibernate boot fail)
**Nguyên nhân**: service boot không có profile → datasource (chỉ định nghĩa trong profile) không nạp.
**Fix (đã áp)**: base `application.yml` dùng `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}` + plugin pin `dev`. Nếu vẫn gặp: chạy explicit `SPRING_PROFILES_ACTIVE=dev` hoặc `-Dspring-boot.run.profiles=dev`.

## Service không boot: `jwt` bind null
**Nguyên nhân**: dùng flat key `smartfarm.security.issuer` thay vì nested `smartfarm.security.jwt.issuer` (ISSUE-01/CFG-01).
**Fix**: dùng nested form (xem [configuration.md](configuration.md)).

## Event identity không tới browser (WS)
**Nguyên nhân**: identity emit JSON, WS bridge expect protobuf (EVT-01). Đang open.

## Health service không gọi được từ gateway
**Nguyên nhân**: health orphan — gateway chưa khai báo gRPC client (GW-01). Đang open.

## MQTT không nhận event
Kiểm tra `SMARTFARM_MQTT_EVENTS_ENABLED` (default `false` ở gateway) và broker `tcp://localhost:1883` chạy (`docker compose up -d mqtt`).

← [Configuration](configuration.md) · [Unresolved Items](../audit/unresolved-items.md)
