# V3.3 Environment Rule Engine

## Rule model

A rule should contain:

- farm/zone scope
- metric
- operator
- threshold
- duration
- hysteresis
- target actuator
- desired state
- cooldown
- priority
- schedule
- enabled
- fail-safe behavior

## Example: heater

Temperature below lower threshold
for configured duration
→ heater ON.

Temperature above upper threshold
→ heater OFF.

Use hysteresis to prevent rapid ON/OFF oscillation.

## Example: ventilation

Humidity above configured threshold
or gas concentration above configured threshold
for configured duration
→ ventilation ON.

Return below safe threshold
for configured duration
→ ventilation OFF.

## Checklist

- [ ] Threshold.
- [ ] Hysteresis.
- [ ] Duration.
- [ ] Cooldown.
- [ ] Priority.
- [ ] Schedule.
- [ ] Manual override.
- [ ] Fail-safe.
- [ ] Rule version.
- [ ] Audit.

## Test

- [ ] Boundary value.
- [ ] Hysteresis.
- [ ] Sensor spike.
- [ ] Sensor silence.
- [ ] Conflicting rules.
- [ ] Manual override.
- [ ] Restart recovery.
