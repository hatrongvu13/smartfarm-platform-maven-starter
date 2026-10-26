# Tập 00 — Tổng quan: "Cắm là chạy" nghĩa là gì

## Hook (câu mở)
"Lib security chung hứa: service mới chỉ cần thêm cấu hình là chạy. Thật không? Tôi thử onboard một service và nó chết ngay lúc khởi động."

## Bối cảnh
Nền tảng SmartFarm có một thư viện dùng chung: `libs/smartfarm-security`.
Nó tự động cấu hình (auto-config) bảo mật cho mọi service: xác thực JWT,
chính sách gRPC, bảo mật MQTT. Ý tưởng là service mới chỉ khai vài dòng
cấu hình, không viết code bảo mật.

Các lớp auto-config (đăng ký trong
`libs/smartfarm-security/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`):
- `SmartFarmSecurityPropertiesAutoConfiguration` — nạp cấu hình `smartfarm.security.*`
- `SmartFarmGrpcSecurityAutoConfiguration` — chặn gRPC
- `ServletResourceSecurity` / `ReactiveResourceSecurity` — chặn HTTP
- `SmartFarmMqttSecurityAutoConfiguration` — ký/kiểm MQTT
- `SmartFarmAuthorizationFreshnessAutoConfiguration` — làm mới quyền

## Hợp đồng cấu hình (điều lib BẮT BUỘC service khai)
Nguồn: `libs/smartfarm-security/src/main/java/com/htv/smartfarm/security/config/SmartFarmSecurityProperties.java`

- `smartfarm.security.jwt` — BẮT BUỘC (thiếu là ném lỗi lúc tạo bean).
- Trong `jwt`: `issuer`, `jwk-set-uri` (BẮT BUỘC, không null), `audiences` (BẮT BUỘC, không rỗng).
- `grpc`, `mqtt`, `correlation` — có mặc định, không khai cũng chạy.

## Thông điệp chính của series
"Cắm là chạy" chỉ đúng khi hợp đồng cấu hình **rõ ràng và thống nhất**.
Ở đây hợp đồng bị khai mỗi service một kiểu, nên vẫn có service chết lúc
khởi động dù "chỉ thiếu cấu hình". Series này đi qua 6 bất cập, từ nặng
đến nhẹ.

## Danh sách tập
1. Thiếu khối `jwt` (farm-simulator) — chặn boot
2. Prod thiếu khai lại `jwt` (reporting, finance) — chặn boot
3. Lệch tên biến env issuer — token bị từ chối
4. Hai endpoint JWKS — discovery và config lệch nhau
5. Route auth ghi cứng trong lib chung — mở quá rộng
6. gRPC identity không check scope — service quyền cao gác yếu

## Lời thoại gợi ý (voice-over)
"Một thư viện dùng chung tốt phải làm một việc khó: biến bảo mật thành thứ
cắm vào là chạy. Nhưng ranh giới giữa 'cắm là chạy' và 'cắm là chết' chỉ là
vài dòng YAML. Hôm nay ta xem ranh giới đó nằm ở đâu."
