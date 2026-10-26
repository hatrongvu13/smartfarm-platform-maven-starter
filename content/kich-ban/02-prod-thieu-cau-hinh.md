# Tập 02 — Prod thiếu khai lại `jwt`: chạy dev ổn, lên prod chết

**Mức: CHẶN BOOT (BLOCKER)**

## Hook
"Chạy dev ngon lành. Deploy lên prod. Chết. Mà chẳng ai đổi dòng code nào."

## Bối cảnh
Hai service `reporting` và `finance` khai `jwt` trong file base với giá trị
mặc định RỖNG:
```yaml
jwt:
  issuer: ${SMARTFARM_JWT_ISSUER:}      # mặc định rỗng
  jwk-set-uri: ${SMARTFARM_JWKS_URI:}   # mặc định rỗng
```
File prod của hai service này **không khai lại** khối `jwt`. Nên khi chạy
prod, nếu biến môi trường `SMARTFARM_JWT_ISSUER` chưa set, giá trị là chuỗi
rỗng → validator ném lỗi.

## Tái hiện (quay màn hình)
```bash
# Prod của reporting/finance KHÔNG có key jwt nào
grep -n "jwt\|issuer\|jwk" services/smartfarm-reporting-service/src/main/resources/application-prod.yml
grep -n "jwt\|issuer\|jwk" services/smartfarm-finance-service/src/main/resources/application-prod.yml
# -> cả hai: không có dòng nào

# Base lại đặt mặc định rỗng
grep -n "SMARTFARM_JWT_ISSUER" services/smartfarm-reporting-service/src/main/resources/application.yml
```

## Biểu hiện
Khởi động prod không env → `issuer`/`jwk-set-uri` rỗng → chuỗi rỗng là
"blank" → `IllegalArgumentException` tại
`libs/smartfarm-security/.../config/SecurityPropertiesValidator.java`.

So với service làm đúng (inventory base, order/health prod) dùng
`${SMARTFARM_JWT_ISSUER}` **không có default** → nếu quên env thì vẫn chết,
nhưng chết với lỗi rõ "biến chưa set", dễ chẩn hơn chuỗi rỗng.

## Nguyên nhân gốc
Ba kiểu khai mặc định issuer lẫn lộn trong dự án:
1. Không default: `${SMARTFARM_JWT_ISSUER}` — tốt, fail rõ.
2. Default rỗng: `${SMARTFARM_JWT_ISSUER:}` — xấu, fail mơ hồ (reporting, finance).
3. Default localhost: `${IDENTITY_ISSUER:http://localhost:8092}` — nguy hiểm nếu lọt lên prod.

## Cách sửa
Bỏ default rỗng ở base của reporting và finance, hoặc khai `jwt` ở prod:
```yaml
# reporting/finance application.yml (base) — đổi:
jwt:
  issuer: ${SMARTFARM_JWT_ISSUER}       # bỏ dấu : rỗng
  jwk-set-uri: ${SMARTFARM_JWKS_URI}
```

## Lời thoại gợi ý
"Một giá trị mặc định rỗng tệ hơn không có mặc định. Không có mặc định thì
nó chết ngay và nói rõ 'thiếu biến'. Mặc định rỗng thì nó chết muộn hơn,
với một thông báo khó hiểu hơn. Nhất quán cách khai cấu hình bắt buộc còn
quan trọng hơn chính giá trị."
