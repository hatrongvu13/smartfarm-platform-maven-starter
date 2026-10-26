# Tập 03 — Lệch tên biến env issuer: token hợp lệ vẫn bị từ chối

**Mức: CAO (HIGH)**

## Hook
"Identity ký token với issuer này. Order kiểm token với issuer kia. Cùng một chỗ, hai tên biến. Lệch một dấu gạch chéo là sập cả hệ."

## Bối cảnh
`issuer` (claim `iss`) phải khớp **từng ký tự** giữa bên ký và bên kiểm.
Spring kiểm bằng so sánh chuỗi tuyệt đối. `http://localhost:8092` khác
`http://localhost:8092/` (thừa dấu `/`).

Nhưng dự án đọc issuer từ **hai tên biến môi trường khác nhau**:
- Identity (bên ký): `${IDENTITY_ISSUER}` — `application-prod.yml`
- Resource server (bên kiểm): `${SMARTFARM_JWT_ISSUER}` — `application-prod.yml`

Không gì ép hai biến này bằng nhau. Người vận hành set một, quên cái kia,
hoặc set lệch dấu `/`, là mọi token bị từ chối.

## Tái hiện (quay màn hình)
```bash
# Bên ký đọc IDENTITY_ISSUER
grep -n "issuer" services/smartfarm-identity-service/src/main/resources/application-prod.yml

# Bên kiểm đọc SMARTFARM_JWT_ISSUER
grep -n "issuer" services/smartfarm-order-service/src/main/resources/application-prod.yml
```
Dựng lỗi: set `IDENTITY_ISSUER=https://id.smartfarm.vn` và
`SMARTFARM_JWT_ISSUER=https://id.smartfarm.vn/` (thừa `/`). Gọi API qua gateway.

## Biểu hiện
Login thành công, lấy được token. Nhưng gọi order API → `401 Unauthorized`.
Log resource server: `Invalid issuer`. Người mới nhìn tưởng token sai, thật
ra chỉ lệch một ký tự trong cấu hình.

## Nguyên nhân gốc
- Ký `iss`: `TokenService.java` (`.issuer(settings.issuer())`), từ `smartfarm.identity.issuer`.
- Kiểm `iss`: `SmartFarmJwtValidator.java` → `JwtValidators.createDefaultWithIssuer(...)`, từ `smartfarm.security.jwt.issuer`.
- Hai property, hai biến env, một giá trị bắt buộc giống nhau.

## Cách sửa
Mọi service (ký lẫn kiểm) đọc **cùng một biến**, và chuẩn hoá bỏ `/` cuối:
```yaml
# dùng chung một tên biến khắp fleet
issuer: ${SMARTFARM_JWT_ISSUER}
# identity cũng đọc đúng biến đó cho smartfarm.identity.issuer
```
Thêm một test khởi động so hai giá trị, hoặc một script kiểm env trước deploy.

## Lời thoại gợi ý
"Hai cái tên cho một sự thật là lời mời cho sai lệch. Khi một giá trị phải
giống nhau ở nhiều nơi, đừng cho nó nhiều tên. Một biến, một nguồn sự thật."
