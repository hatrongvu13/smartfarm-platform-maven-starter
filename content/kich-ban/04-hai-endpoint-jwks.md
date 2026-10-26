# Tập 04 — Hai endpoint JWKS: discovery chỉ một đường, config trỏ đường khác

**Mức: CAO (HIGH)** — hiện là lỗi tiềm ẩn, chưa vỡ nhưng sẽ vỡ

## Hook
"OIDC có một quy ước đẹp: hỏi discovery, nó bảo lấy khoá ở đâu. Service mới làm đúng quy ước đó... và lấy khoá nhầm chỗ."

## Bối cảnh
Identity service có **hai** nơi phát khoá công khai (JWKS):
1. Chuẩn OIDC, do Spring Authorization Server tự phát: `<issuer>/oauth2/jwks`.
   Discovery metadata quảng bá `jwks_uri` trỏ về đây.
2. Tự viết tay: `/.well-known/jwks.json` trong `AuthController`.

**Mọi service** hiện cấu hình `jwk-set-uri = .../.well-known/jwks.json` —
tức là đường tự viết. Hiện tại hai đường phát cùng một khoá nên chưa vỡ.
Nhưng một service mới làm "đúng chuẩn" (đọc discovery để biết `jwks_uri`)
sẽ lấy khoá ở `/oauth2/jwks`. Hai đường phải giữ đồng bộ mãi mãi.

## Tái hiện (quay màn hình)
```bash
# Discovery quảng bá /oauth2/jwks (khẳng định trong test)
grep -rn "oauth2/jwks" services/smartfarm-identity-service/src/test

# Nhưng endpoint tự viết là /.well-known/jwks.json
grep -n "jwks" services/smartfarm-identity-service/src/main/java/com/htv/smartfarm/identity/api/AuthController.java

# Và mọi service trỏ về đường tự viết
grep -rn "jwk-set-uri" services/*/src/main/resources/*.yml | grep well-known
```
So hai đường trả về:
```bash
curl -s http://localhost:8092/.well-known/openid-configuration | jq .jwks_uri
curl -s http://localhost:8092/oauth2/jwks
curl -s http://localhost:8092/.well-known/jwks.json
```

## Biểu hiện
Chưa lỗi hôm nay. Vỡ khi: (a) đổi/xoay khoá ở một đường mà quên đường kia,
hoặc (b) service mới onboard theo discovery → lấy khoá ở path khác path đã
được "cắm là chạy" chỉ. Khi đó token không verify được, 401 bí ẩn.

## Nguyên nhân gốc
Một endpoint thừa. Chuẩn OIDC đã có `/oauth2/jwks`; cái tự viết
`/.well-known/jwks.json` là trùng lặp, tạo hai nguồn sự thật cho khoá.

## Cách sửa
Chọn một đường duy nhất.

**A. Bỏ cái tự viết, theo chuẩn** (khuyến nghị):
- Xoá `AuthController.jwks()` và dòng permit-all tương ứng.
- Mọi service đổi `jwk-set-uri` thành `${issuer}/oauth2/jwks`.

**B. Giữ tên `.well-known/jwks.json`, chỉnh discovery trỏ về nó:**
```java
AuthorizationServerSettings.builder()
    .issuer(issuer)
    .jwkSetEndpoint("/.well-known/jwks.json")
    .build();
```

## Lời thoại gợi ý
"Hai endpoint cho cùng một khoá là hai nguồn sự thật. Hôm nay chúng trùng
nhau, nên chẳng ai thấy vấn đề. Nhưng 'cắm là chạy' có nghĩa là tin vào
discovery — và discovery đang chỉ sang chỗ không ai dùng. Hãy để lại một
đường thôi."
