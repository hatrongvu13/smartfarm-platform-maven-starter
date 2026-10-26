# Tập 05 — Route auth ghi cứng trong lib chung: mọi service đều mở

**Mức: CAO (HIGH)**

## Hook
"Service báo cáo chẳng có trang đăng nhập. Nhưng path `/api/v1/auth/login` của nó vẫn mở công khai. Vì sao? Vì nó dùng lib chung."

## Bối cảnh
`ServletResourceSecurity` trong lib chung ghi **cứng** danh sách path được
mở công khai (`permitAll`), gồm các route riêng của identity service:
`/api/v1/auth/register`, `/login`, `/refresh`, `/logout`,
`/mfa/verify`, `/mfa/enrollment/begin`, `/mfa/enrollment/confirm`,
`/bootstrap-superadmin`, `/.well-known/jwks.json`.

Lib này auto-config cho **mọi** service web bật `smartfarm.security.http.enabled=true`.
Nên service nào cũng tự động mở các path đó — dù không có controller nào ở đó.

## Tái hiện (quay màn hình)
```bash
# Danh sách permitAll ghi cứng ngay trong lib dùng chung
grep -n "permitAll\|/api/v1/auth\|mfa" \
  libs/smartfarm-security/src/main/java/com/htv/smartfarm/security/web/ServletResourceSecurity.java
```
Không có property nào để service tự khai danh sách public của riêng mình.

## Biểu hiện
- Bề mặt tấn công thừa: mọi service web khai báo mở các path auth, dễ gây
  nhầm khi rà soát bảo mật.
- Không "cấu hình được": muốn một service mở path khác, hoặc không mở path
  auth, phải sửa code lib chung — trái tinh thần "chỉ cấu hình".

## Nguyên nhân gốc
Chính sách riêng của một service (identity) bị nhét vào thư viện dùng chung.
Lib nên cung cấp cơ chế, không nên gói sẵn chính sách của một service.

## Cách sửa
Đưa danh sách public ra property, để mỗi service tự khai:
```java
// trong ServletResourceSecurity: đọc từ cấu hình
properties.http().publicGetPaths().forEach(p -> auth.requestMatchers(GET, p).permitAll());
properties.http().publicPostPaths().forEach(p -> auth.requestMatchers(POST, p).permitAll());
```
```yaml
# identity application.yml tự khai route của mình
smartfarm:
  security:
    http:
      enabled: true
      public-post-paths:
        - /api/v1/auth/login
        - /api/v1/auth/refresh
        - /api/v1/auth/mfa/verify
      public-get-paths:
        - /.well-known/jwks.json
```
Service khác không khai gì → không mở path nào (ngoài health).

## Lời thoại gợi ý
"Một thư viện dùng chung nên cho cơ chế, không nên gói sẵn chính sách của
một người dùng. Khi chính sách của identity nằm trong lib, mọi service đều
mặc nó. Hãy để chính sách ở nơi nó thuộc về — file cấu hình của từng service."
