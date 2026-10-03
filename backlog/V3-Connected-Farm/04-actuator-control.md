# V3.4 Heater / Ventilation Control

## Heater

State:

OFF → TURNING_ON → ON → TURNING_OFF → OFF

Safety:

- [ ] Maximum continuous runtime.
- [ ] Over-temperature cutoff.
- [ ] Sensor missing behavior.
- [ ] Command timeout.
- [ ] Device acknowledgement.
- [ ] Manual emergency OFF.

## Ventilation fan

- [ ] Minimum ON time.
- [ ] Minimum OFF time.
- [ ] Maximum runtime.
- [ ] Humidity rule.
- [ ] Gas rule.
- [ ] Manual override.
- [ ] Emergency ON policy if configured.

## Command lifecycle

Issued
→ Sent
→ Acknowledged
→ Applied
or
→ Failed/Expired

## Test

- [ ] Duplicate command.
- [ ] Device offline.
- [ ] Command timeout.
- [ ] ACK lost.
- [ ] Relay remains ON unexpectedly.
- [ ] Sensor unavailable.
- [ ] Restart while actuator is ON.
