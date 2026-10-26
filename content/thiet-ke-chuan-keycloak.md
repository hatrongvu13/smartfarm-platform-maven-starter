# Thiết kế chuẩn Identity theo Keycloak (bản tham chiếu)

Dùng làm "cái thước" để so với identity tự viết của SmartFarm. Keycloak là
một Identity Provider (IdP) mã nguồn mở, triển khai đầy đủ OAuth2 và OIDC.

## 1. Keycloak cho bạn sẵn những gì

| Khối | Keycloak làm sẵn | Bạn chỉ cần |
|------|------------------|-------------|
| Cấp & ký token | Có (RS256, tự xoay khoá) | Cấu hình realm |
| JWKS endpoint | 1 endpoint chuẩn duy nhất | Không làm gì |
| OIDC discovery | Tự phát `.well-known/openid-configuration` | Không làm gì |
| Đăng nhập / MFA / TOTP | Có sẵn, bật bằng cấu hình | Tick chọn |
| Refresh token + thu hồi | Có | Không làm gì |
| Quản lý user / role / group | Có UI + Admin REST API | Gọi API |
| Multi-tenant | Realm (mỗi tenant 1 realm) hoặc client-scope | Tạo realm |
| Xoay khoá (key rotation) | Tự động, nhiều khoá song song | Không làm gì |

Ý tưởng cốt lõi: **IdP là một sản phẩm, không phải code bạn viết**. Service
của bạn chỉ là "resource server" — nhận token, kiểm chữ ký, đọc quyền.

## 2. Các khái niệm chuẩn

- **Realm**: không gian cô lập (user, role, client riêng). Multi-tenant
  thường = mỗi tenant một realm, hoặc một realm chung chia theo group.
- **Client**: một ứng dụng xin token (gateway, mobile app, service).
  Mỗi client có `client_id`, và `audience` của token thường là client đích.
- **Client scope / role**: quyền gắn vào token (claim `realm_access.roles`,
  `resource_access.<client>.roles`, hoặc `scope`).
- **Issuer (`iss`)**: `https://<host>/realms/<realm>`. **Một giá trị duy nhất**,
  mọi nơi lấy từ discovery, không ai gõ tay.

## 3. Hai endpoint sống còn (chuẩn OIDC)

```
GET  /realms/<realm>/.well-known/openid-configuration
     -> trả JSON: issuer, jwks_uri, token_endpoint, ...
GET  /realms/<realm>/protocol/openid-connect/certs   (jwks_uri)
     -> trả khoá công khai (JWKS)
```

Quy tắc vàng: **resource server không cấu hình cứng `jwks_uri`**. Nó chỉ
biết `issuer`, rồi đọc discovery để tự tìm `jwks_uri`. Spring Boot làm điều
này khi bạn chỉ khai:
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://id.example.com/realms/smartfarm
          # KHÔNG cần jwk-set-uri — lấy từ discovery
```
Đổi/xoay khoá ở IdP → resource server tự cập nhật qua JWKS, không ai sửa config.

## 4. Luồng token chuẩn

```mermaid
sequenceDiagram
    participant App as Client (gateway/app)
    participant KC as Keycloak (IdP)
    participant RS as Resource Server (order...)
    App->>KC: POST /token (login / client_credentials)
    KC-->>App: access_token (JWT ký RS256) + refresh_token
    App->>RS: gọi API kèm Bearer token
    RS->>KC: lấy JWKS (1 lần, cache; tìm qua discovery)
    KC-->>RS: khoá công khai
    RS->>RS: kiểm iss, aud, exp, chữ ký, roles
    RS-->>App: kết quả
```

## 5. Vì sao chuẩn này ít bất cập

1. **Một nguồn sự thật cho khoá**: chỉ một JWKS endpoint, resource server
   tìm nó qua discovery → không bao giờ lệch.
2. **Một nguồn sự thật cho issuer**: resource server khai `issuer-uri`,
   mọi thứ khác (jwks, token endpoint) suy ra từ đó → không có chuyện hai
   tên biến cho một giá trị.
3. **Chính sách nằm ở IdP và ở từng service**, không nhét vào lib dùng chung.
4. **Xoay khoá an toàn**: nhiều khoá song song theo `kid`, token cũ vẫn
   verify được trong lúc chuyển.
5. **Không tự viết chỗ dễ sai**: ký, xoay khoá, refresh, MFA — đều là phần
   đã được kiểm nghiệm, không phải code mới.

## 6. Khi nào KHÔNG cần Keycloak

Tự viết IdP hợp lý khi: nghiệp vụ tenant/role quá đặc thù, không muốn thêm
một hệ phải vận hành, hoặc cần nhúng sâu vào domain. Đánh đổi: bạn phải tự
làm đúng mọi thứ Keycloak làm sẵn ở trên — và đó chính là nơi 6 bất cập của
dự án sinh ra.
