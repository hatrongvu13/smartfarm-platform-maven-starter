# ADR-0004: Resource server tự kiểm JWT bằng JWKS cache

**Trạng thái:** Đã chốt.

**Bối cảnh.** Gọi identity (introspection) mỗi request làm farm phụ thuộc đồng bộ vào identity: identity chậm thì farm chậm, identity sập thì farm sập.

**Quyết định.** Farm dùng `NimbusJwtDecoder` tải JWKS từ identity (qua mTLS ở profile `mtls`), cache khóa, tự kiểm chữ ký, `iss`, `exp`, `nbf`, `aud`. Gặp `kid` lạ thì tải lại JWKS. Identity công bố cả khóa đang dùng và khóa nghỉ để đổi khóa không làm hỏng token đang sống.

**Hệ quả.**
- Thu hồi không tức thì: token sống tối đa 5 phút sau khi quyền bị đổi.
- Cache dùng mặc định của Nimbus, chưa cấu hình thời hạn riêng; chưa có test đổi khóa phía farm (M6).

**Bằng chứng.** [`MtlsConfiguration` farm](../../../services/farm-service/src/main/java/com/htv/farmverse/farm/config/MtlsConfiguration.java), [`FarmVerseJwtValidators`](../../../libs/farmverse-starter-service/src/main/java/com/htv/farmverse/service/security/FarmVerseJwtValidators.java), [`SigningKeyConfiguration`](../../../services/identity-service/src/main/java/com/htv/farmverse/identity/oauth2/SigningKeyConfiguration.java), `SigningKeyRotationTest`.
