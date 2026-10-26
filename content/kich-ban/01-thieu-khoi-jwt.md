# Tập 01 — Thiếu khối `jwt`: service chết ngay lúc khởi động

**Mức: CHẶN BOOT (BLOCKER)**

## Hook
"Service này chỉ khai URL của MQTT. Không khai gì về JWT. Nó kéo lib security vào. Bấm chạy... và chết."

## Bối cảnh
`smartfarm-farm-simulator` phụ thuộc `smartfarm-security` (xác nhận trong
`platform/smartfarm-farm-simulator/pom.xml`, dòng 29). Nhưng file cấu hình
`platform/smartfarm-farm-simulator/src/main/resources/application.yml`
chỉ đặt `smartfarm.mqtt.url`, không có khối `smartfarm.security.jwt`.

Lib security tự động nạp cấu hình. Khi không thấy `jwt`, hàm khởi tạo ném lỗi.

## Tái hiện (quay màn hình)
```bash
# 1. Xác nhận simulator có dùng lib security
grep -n "smartfarm-security" platform/smartfarm-farm-simulator/pom.xml

# 2. Xác nhận nó KHÔNG khai jwt
grep -n "jwt\|issuer\|jwk" platform/smartfarm-farm-simulator/src/main/resources/application.yml
# -> không có dòng nào

# 3. Chạy thử (cần mvn)
mvn -pl platform/smartfarm-farm-simulator spring-boot:run
```

## Biểu hiện
Ứng dụng không khởi động. Log báo đại ý:
```
IllegalArgumentException: smartfarm.security.jwt is required
```
Nguồn lỗi: hàm khởi tạo trong
`libs/smartfarm-security/.../config/SmartFarmSecurityProperties.java`:
```java
if (jwt == null) {
    throw new IllegalArgumentException("smartfarm.security.jwt is required");
}
```

## Nguyên nhân gốc
Lib bắt buộc `jwt` không null, nhưng simulator là service "nền" (chỉ phát
dữ liệu giả), người viết nghĩ nó không cần JWT nên bỏ qua. Lib không có
chế độ "tắt bảo mật cho service này".

## Cách sửa
Chọn một trong hai:

**A. Thêm khối jwt loopback** (nếu simulator sẽ gọi API có bảo vệ):
```yaml
smartfarm:
  security:
    jwt:
      issuer: ${IDENTITY_ISSUER:http://localhost:8092}
      jwk-set-uri: ${IDENTITY_JWK_SET_URI:http://localhost:8092/.well-known/jwks.json}
      audiences:
        - smartfarm-simulator
      allow-local-http: true
```

**B. Loại auto-config security** (nếu simulator không cần bảo mật):
```java
@SpringBootApplication(exclude = {
    SmartFarmSecurityPropertiesAutoConfiguration.class,
    SmartFarmGrpcSecurityAutoConfiguration.class
})
```

## Lời thoại gợi ý
"Bài học: khi một thư viện bắt buộc một khối cấu hình, nó phải cho bạn một
cách để nói 'tôi không cần'. Nếu không, mọi service kéo nó vào đều buộc
phải khai — kể cả service chẳng liên quan gì tới đăng nhập."
