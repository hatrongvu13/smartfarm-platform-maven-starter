# Content — Bất cập khi triển khai Security + OIDC "cắm là chạy"

Thư mục này chứa đủ kịch bản để quay video hoặc làm nội dung về các vấn đề
gặp phải khi onboard một service mới vào nền tảng SmartFarm, sau khi refactor
OIDC ở identity service và lib `smartfarm-security` chung.

## Cách dùng

Mỗi file trong `kich-ban/` là một tập (episode) độc lập. Quay theo thứ tự số
là một series đầy đủ, hoặc lấy lẻ từng tập.

| Tập | File | Mức | Chủ đề |
|-----|------|-----|--------|
| 00 | `kich-ban/00-tong-quan.md` | — | Giới thiệu: "cắm là chạy" là gì, kiểm tra ra sao |
| 01 | `kich-ban/01-thieu-khoi-jwt.md` | Chặn boot | Service thiếu khối `jwt` → không khởi động |
| 02 | `kich-ban/02-prod-thieu-cau-hinh.md` | Chặn boot | Prod không khai lại `jwt`, kế thừa giá trị rỗng |
| 03 | `kich-ban/03-lech-ten-bien-issuer.md` | Cao | Hai tên biến env cho cùng một issuer |
| 04 | `kich-ban/04-hai-endpoint-jwks.md` | Cao | Discovery chỉ một đường, config trỏ đường khác |
| 05 | `kich-ban/05-route-hardcode.md` | Cao | Route auth ghi cứng trong lib chung |
| 06 | `kich-ban/06-grpc-identity-khong-scope.md` | Vừa | Identity gRPC không check quyền |

## So sánh với chuẩn (Keycloak)

- `thiet-ke-chuan-keycloak.md` — thiết kế identity chuẩn theo Keycloak, làm thước đo
- `so-sanh-keycloak-vs-tu-viet.md` — bảng so trực diện + 3 cách kể chuyện cho video

## Sơ đồ

- `so-do/kien-truc-tong-the.md` — luồng token end-to-end (Mermaid)
- `so-do/luong-loi-boot.md` — chuỗi nhân-quả của lỗi chặn boot (Mermaid)

## Cấu trúc mỗi kịch bản

```
- Tóm tắt một câu (hook mở video)
- Bối cảnh (vì sao có vấn đề)
- Tái hiện (lệnh/config để dựng lại lỗi — quay màn hình)
- Biểu hiện (lỗi hiện ra thế nào)
- Nguyên nhân gốc (chỉ vào file:dòng)
- Cách sửa (minimal fix, có đoạn code)
- Lời thoại gợi ý (voice-over)
```

## Nguồn

Rà soát ngày 2026-10-26 trên nhánh `main`. File/dòng tham chiếu đúng tại thời
điểm đó; kiểm lại nếu code đã đổi.
