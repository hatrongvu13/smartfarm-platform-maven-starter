# No-Rework Strategy

## Freeze in V1

- [ ] ID strategy.
- [ ] Farm/Zone scope.
- [ ] Coordinate convention.
- [ ] Event envelope.
- [ ] Task state machine.
- [ ] Device abstraction.
- [ ] Telemetry envelope.
- [ ] Command envelope.
- [ ] Audit pattern.
- [ ] Idempotency pattern.

## V2 adds

- StorageLocationId.
- Warehouse structure.
- QR resolver.
- Category/storage policy.

## V3 adds

- Real devices.
- MQTT.
- Telemetry persistence.
- Rule engine.
- Heater/fan commands.
- Safety and automation.

## Never make these identities

- QR as domain primary key.
- MQTT client ID as domain primary key.
- Sensor serial as domain primary key.
- Relay address as domain primary key.
