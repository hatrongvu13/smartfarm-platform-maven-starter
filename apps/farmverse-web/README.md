# farmverse-web

React + Vite + TanStack Query + Tailwind. Không giữ token: đăng nhập và gọi API qua gateway (BFF).

```bash
npm install
npm run dev        # :5173 — mở qua gateway: http://127.0.0.1:8080
npm run build
```

| Thư mục | Nội dung |
|---|---|
| `src/lib/api.ts` | `fetch` với cookie + `X-XSRF-TOKEN`, đường `/api/<service>/...`, lỗi ProblemDetail |
| `src/lib/auth.tsx` | trạng thái đăng nhập từ `GET /bff/me`, `hasPermission` |
| `src/pages/` | Tổng quan, Trang trại, Hồ sơ (+ TOTP), admin: Người dùng, Phân quyền |

Ẩn/hiện theo permission chỉ để hiển thị; service phía sau luôn tự kiểm quyền.
