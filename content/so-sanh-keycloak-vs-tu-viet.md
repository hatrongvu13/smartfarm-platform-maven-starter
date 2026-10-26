# So sánh: Keycloak (chuẩn) vs Identity tự viết của SmartFarm

Bản này là "xương sống" cho nội dung hấp dẫn: lấy một chuẩn ai cũng công
nhận (Keycloak) làm thước, soi dự án tự viết, chỉ ra từng chỗ lệch và vì sao.

## Bảng so sánh tổng

| Hạng mục | Keycloak (chuẩn) | SmartFarm tự viết | Lệch? |
|----------|------------------|-------------------|-------|
| Cấp & ký token RS256 | Có sẵn | Tự viết (`TokenService`, `RsaJwtIssuer`) | Làm được |
| JWKS endpoint | **1** endpoint duy nhất | **2** endpoint (`/oauth2/jwks` + `/.well-known/jwks.json`) | ⚠️ Tập 04 |
| Resource server tìm khoá | Qua discovery, tự động | Cấu hình cứng `jwk-set-uri` | ⚠️ dễ lệch |
| Issuer | 1 giá trị, suy từ discovery | 1 giá trị nhưng **2 tên biến env** | ⚠️ Tập 03 |
| Service mới onboard | Khai `issuer-uri`, xong | Khai đủ issuer + jwk-set-uri + audiences, thiếu là chết | ⚠️ Tập 01,02 |
| Chính sách public path | Mỗi client/service tự khai | **Ghi cứng trong lib chung** | ⚠️ Tập 05 |
| Phân quyền theo scope | Role/scope trong token, service kiểm | Có cơ chế, nhưng identity **không khai** | ⚠️ Tập 06 |
| Xoay khoá (rotation) | Tự động, nhiều `kid` song song | Phải tự quản 2 endpoint đồng bộ | ⚠️ rủi ro |
| MFA / TOTP | Bật bằng cấu hình | Tự viết (đã có) | Làm được |
| Refresh + thu hồi | Có sẵn | Tự viết (`RefreshSession`) | Làm được |
| TTL token | 1 chính sách / client | **3 chính sách** rời nhau (10m / 5m / cap 15m) | ⚠️ Tập 08* |
| Vận hành | Thêm 1 hệ phải nuôi | Không thêm hệ, nhưng gánh mọi rủi ro trên | Đánh đổi |

\* TTL là phát hiện phụ trong rà soát OIDC, không có tập riêng.

## Đọc bảng: ba nhóm

**Nhóm "làm được như chuẩn"** — ký token, MFA, refresh. Dự án tự viết đúng,
không thua Keycloak. Đây là phần nên khoe.

**Nhóm "lệch do thiết kế, sửa được"** — 2 JWKS, 2 tên biến issuer, route
hardcode, identity thiếu scope. Đều do tự viết lại phần Keycloak làm sẵn,
và làm thiếu một lớp "một nguồn sự thật". Sửa được, đã có cách ở các tập.

**Nhóm "đánh đổi vận hành"** — tự viết thì không phải nuôi thêm một hệ
Keycloak, nhưng phải tự chịu trách nhiệm cho mọi dòng ở nhóm hai.

## Điểm mấu chốt (dùng làm thông điệp video)

> Keycloak không thông minh hơn bạn. Nó chỉ **ép một nguồn sự thật** cho ba
> thứ: khoá (một JWKS), issuer (một giá trị suy từ discovery), và chính sách
> (ở client, không ở thư viện chung). Dự án tự viết sao chép đủ tính năng
> nhưng để mỗi thứ có hai nguồn sự thật — và mọi bất cập đều mọc ra từ đó.

## Ba cách kể chuyện gợi ý

1. **"Tôi tự viết Keycloak và đây là 6 cái bẫy"** — kể theo thứ tự tập 01→06,
   mỗi tập đối chiếu "Keycloak làm thế nào ở chỗ này".
2. **"Một nguồn sự thật"** — một tập duy nhất, lấy 3 cặp lệch (khoá, issuer,
   chính sách), chứng minh mọi lỗi đều là biến thể của cùng một nguyên tắc.
3. **"Khi nào nên tự viết IdP?"** — dùng bảng đánh đổi, kết bằng: tự viết
   được, nhưng phải trả giá bằng việc tự giữ các nguồn sự thật.

## Bản đồ tập ↔ hạng mục lệch

| Hạng mục lệch | Tập kịch bản | Keycloak tránh bằng cách |
|---------------|--------------|--------------------------|
| 2 JWKS endpoint | 04 | Chỉ 1 endpoint, tìm qua discovery |
| 2 tên biến issuer | 03 | Chỉ khai `issuer-uri`, suy ra phần còn lại |
| Service thiếu cấu hình → chết | 01, 02 | `issuer-uri` là tất cả những gì cần |
| Route public hardcode | 05 | Chính sách ở client/service, không ở lib |
| Identity gRPC thiếu scope | 06 | Role/scope trong token, kiểm ở mỗi service |
