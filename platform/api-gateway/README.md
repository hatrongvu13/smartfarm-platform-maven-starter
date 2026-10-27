# api-gateway (BFF)

Cửa vào duy nhất cho trình duyệt: `http://127.0.0.1:8080`.

| Đường | Đi đâu | Cần đăng nhập |
|---|---|---|
| `/oauth2/authorization/farmverse` | bắt đầu đăng nhập OIDC (PKCE) với identity | — |
| `/logout` (POST + CSRF) | xóa session, đăng xuất ở identity | có |
| `/bff/me` | thông tin người dùng + quyền (không trả token) | có |
| `/api/identity/**` | identity-service, bỏ tiền tố `/api/identity` | có |
| `/api/farm/**` | farm-service, bỏ tiền tố `/api/farm` | có |
| `/**` | web (Vite `:5173` khi dev) | không |

Token chỉ nằm trong session Redis. Mỗi request `/api/**` được gắn `Authorization: Bearer` bởi filter
`TokenRelay`, tự refresh khi hết hạn. Request ghi phải gửi header `X-XSRF-TOKEN` (lấy từ cookie `XSRF-TOKEN`).

Chạy (identity phải chạy trước):

```bash
docker compose up -d redis
mvn -pl platform/api-gateway spring-boot:run
```
