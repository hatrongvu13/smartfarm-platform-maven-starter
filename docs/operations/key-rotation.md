# Xoay khóa ký JWT

Identity ký token bằng **một** khóa RSA. Khi xoay khóa, khóa cũ vẫn được công bố ở `/oauth2/jwks`
(chỉ khóa công khai) cho tới khi token cuối cùng ký bằng nó hết hạn. Nhờ vậy không ai bị đăng xuất đột ngột.

## Vì sao không đổi ngay

Gateway và farm lấy khóa từ JWKS và chọn theo `kid` trong header token. Đổi khóa ngay thì mọi token
còn hạn (access token sống 5 phút) bị từ chối với 401.

## Các bước

1. Sinh khóa mới: `./scripts/rotate-signing-key.sh key-2026-11`
2. Đặt khóa mới làm khóa ký:

   | Biến môi trường | Giá trị |
   |---|---|
   | `IDENTITY_KEY_ID` | `key-2026-11` |
   | `IDENTITY_PRIVATE_KEY_PATH` | đường dẫn file `key-2026-11-private.pem` |
   | `IDENTITY_PUBLIC_KEY_PATH` | đường dẫn file `key-2026-11-public.pem` |

3. Khai khóa cũ là `retired` (chỉ cần file public của nó):

   | Biến môi trường | Giá trị |
   |---|---|
   | `FARMVERSE_IDENTITY_SIGNINGKEY_RETIRED_0_KEYID` | key-id cũ, ví dụ `dev-key-1` |
   | `FARMVERSE_IDENTITY_SIGNINGKEY_RETIRED_0_PUBLICKEY` | đường dẫn file public cũ |

   Có nhiều khóa nghỉ thì tăng chỉ số: `_1_`, `_2_`...
4. Khởi động lại identity. Log in ra dòng `JWKS công bố 2 khóa: đang ký=..., đã nghỉ=[...]`.
5. Kiểm tra: `/oauth2/jwks` có đủ 2 `kid`. Đăng nhập mới cho token mang `kid` mới.
6. Chờ quá hạn của token dài nhất ký bằng khóa cũ. Mặc định: access token 5 phút, id_token theo cấu hình SAS.
   Refresh token là chuỗi ngẫu nhiên lưu trong DB, không phụ thuộc khóa ký.
7. Gỡ hai biến `RETIRED_0_*` và khởi động lại. Xóa file private cũ.

## Lưu ý

- `key-id` bị trùng giữa khóa đang ký và khóa nghỉ: identity từ chối khởi động.
- Khóa nghỉ chỉ đọc file public. Identity không bao giờ ký lại bằng khóa nghỉ.
- Lộ khóa private (khẩn cấp): xoay khóa và **không** khai khóa cũ làm `retired`. Mọi token cũ bị từ chối ngay.
- Resource server tải lại JWKS khi gặp `kid` lạ, nên không cần khởi động lại gateway hay farm.
- Xoay khóa tự động theo lịch chưa có. Hiện làm tay theo các bước trên.
