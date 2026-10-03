# SmartFarm — Practical Implementation Roadmap V1 → V3

## Mục tiêu

Đây là bản chuẩn hóa thực dụng của SmartFarm. Ba version kế tục nhau nhưng chỉ giữ các capability có giá trị vận hành rõ ràng:

- V1: quản lý trang trại, kho, tồn kho, công việc, vật nuôi, sức khỏe, tài chính và nền tảng IoT.
- V2: kho có vị trí vật lý chính xác, QR location, chính sách lưu trữ và giao nhận.
- V3: connected farm — cảm biến nhiệt độ/độ ẩm/khí, MQTT, rule engine và tự động điều khiển thiết bị như đèn sưởi/quạt.

### Loại khỏi roadmap chính

- 3D warehouse.
- Camera AI.
- Computer vision.
- YouTube streaming.
- Camera evidence.

Các capability trên có thể làm extension trong tương lai nhưng không được trở thành dependency của core.

## Nguyên tắc quan trọng

1. V1 phải khóa identity, event, coordinate, task, device và telemetry contract.
2. V2 chỉ mở rộng spatial thành storage location.
3. V3 chỉ mở rộng device/telemetry thành automation.
4. Sensor là nguồn đo; rule engine là nơi quyết định; actuator là nơi thực thi.
5. Không cho sensor trực tiếp điều khiển actuator mà bỏ qua rule/audit/safety.
6. DEV dùng simulator nhưng phải dùng cùng contract với thiết bị thật ở PROD.
7. Mất MQTT hoặc Internet không được làm hỏng nghiệp vụ cốt lõi.
8. Mọi lệnh điều khiển phải idempotent, có audit và timeout/failsafe.
