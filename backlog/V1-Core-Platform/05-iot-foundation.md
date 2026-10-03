# V1.5 IoT Foundation — Không triển khai automation thật

## Mục tiêu

Chuẩn bị contract để V3 có thể thêm cảm biến và thiết bị điều khiển mà không sửa core.

## Device types

- TEMPERATURE_SENSOR
- HUMIDITY_SENSOR
- GAS_SENSOR
- HEATER
- VENTILATION_FAN
- GATEWAY

## Telemetry envelope

```text
deviceId
farmId
zoneId
metric
value
unit
measuredAt
receivedAt
sequence
schemaVersion
```

## Command envelope

```text
commandId
deviceId
commandType
desiredState
issuedAt
expiresAt
correlationId
```

## Checklist

- [ ] Device identity.
- [ ] Capability model.
- [ ] Telemetry schema.
- [ ] Command schema.
- [ ] Device status.
- [ ] Simulator adapter interface.
- [ ] Real-device adapter interface.
- [ ] Command idempotency.
- [ ] Audit.
