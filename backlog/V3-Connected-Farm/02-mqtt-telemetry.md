# V3.2 MQTT + Telemetry

## Topics

Use a stable namespace such as:

farm/{farmId}/device/{deviceId}/telemetry
farm/{farmId}/device/{deviceId}/state
farm/{farmId}/device/{deviceId}/command

## Checklist

- [ ] MQTT authentication.
- [ ] TLS in PROD.
- [ ] QoS policy.
- [ ] Retained state where appropriate.
- [ ] Duplicate detection.
- [ ] Sequence handling.
- [ ] Out-of-order handling.
- [ ] Offline buffering.
- [ ] Reconnect.
- [ ] Invalid payload quarantine.
- [ ] Telemetry persistence.
- [ ] Aggregation.
- [ ] Device heartbeat.

## Metrics

- temperature °C
- relative humidity %
- gas concentration with explicit sensor unit

Never hard-code a gas metric without declaring the sensor measurement and unit.
