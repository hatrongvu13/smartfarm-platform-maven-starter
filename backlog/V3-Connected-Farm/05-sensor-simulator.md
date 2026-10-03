# V3.5 DEV Simulator / PROD Hardware

## DEV

Simulator must generate:

- normal temperature
- temperature spike
- cold condition
- high humidity
- gas concentration increase
- sensor offline
- noisy readings
- delayed readings

## Simulator requirements

- [ ] Same MQTT topic structure.
- [ ] Same payload schema.
- [ ] Same device identity contract.
- [ ] Deterministic mode for automated tests.
- [ ] Scenario mode for demos.
- [ ] Clock control for tests.

## PROD

Replace simulator adapter with:

- real temperature sensor
- real humidity sensor
- real gas sensor
- real relay/controller
- real fan
- real heater

No business rule code should change between DEV and PROD.
