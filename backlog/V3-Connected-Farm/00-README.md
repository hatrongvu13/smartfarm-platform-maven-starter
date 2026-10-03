# V3 — Connected Farm Automation

## Mục tiêu

Tự động hóa môi trường chăn nuôi dựa trên cảm biến thực tế:

- nhiệt độ
- độ ẩm
- khí độc

và điều khiển:

- đèn sưởi
- quạt thông gió

V3 tập trung vào automation có giá trị vận hành rõ ràng, không đưa 3D/camera/AI vào core.

## Luồng

Sensor
→ MQTT
→ ingestion
→ telemetry
→ rule engine
→ command
→ actuator
→ acknowledgement
→ audit

## Safety principle

Không để sensor gửi lệnh trực tiếp tới relay.

Mọi automation phải đi qua rule + safety + command layer.
