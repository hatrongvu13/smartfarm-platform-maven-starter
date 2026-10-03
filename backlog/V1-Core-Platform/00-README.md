# V1 — Core Farm Operations

## Mục tiêu

Tạo một hệ thống có thể dùng thật cho các nghiệp vụ:

Farm → Warehouse → Inventory → Task → Worker → Livestock → Health → Finance.

V1 chưa cần 3D, camera hoặc automation thật.

## Service/module

- identity-service
- farm-service
- spatial-service
- inventory-service
- work-service
- livestock-service
- health-service
- finance-service
- reporting-service
- gateway
- platform event/outbox/inbox

## Device foundation

V1 chưa triển khai thiết bị thật nhưng phải có abstraction:

DeviceId
DeviceType
DeviceCapability
FarmId
ZoneId
DeviceStatus
CommandId
TelemetryEnvelope

Nhờ vậy V3 không phải sửa lại domain model.
