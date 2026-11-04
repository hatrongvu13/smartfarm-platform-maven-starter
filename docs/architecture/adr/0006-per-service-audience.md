# ADR-0006: Audience riêng cho từng resource server

**Trạng thái:** Đề xuất (chưa làm). Đổi hợp đồng token, cần duyệt.

**Bối cảnh.** Mọi access token có `aud=farmverse-api` ([`FarmVerseTokenCustomizer`](../../../services/identity-service/src/main/java/com/htv/farmverse/identity/oauth2/FarmVerseTokenCustomizer.java)). Một service đọc được token (log, bị chiếm) thì dùng lại token đó ở mọi service khác.

**Phương án.**

| Phương án | Cách làm | Ưu | Nhược |
|---|---|---|---|
| A. Nhiều audience trong một token | `aud=[farm-service, identity-api]` | Gateway không đổi | Vẫn dùng chéo được giữa các audience trong danh sách |
| B. Resource indicator (RFC 8707) | Gateway xin token theo `resource` từng route | Đúng chuẩn | Cần kiểm SAS 7.1 hỗ trợ tới đâu |
| C. Token exchange (RFC 8693) | Gateway đổi token người dùng lấy token `aud=farm-service` trước khi gọi farm | Chặt nhất | Thêm một lời gọi identity mỗi lần token mới, cần cache |

**Đề xuất.** Bắt đầu bằng A có giai đoạn chuyển tiếp (farm chấp nhận `farm-service` hoặc `farmverse-api` qua cờ cấu hình), sau đó thu hẹp. Xác minh khả năng B/C của SAS bằng build thật trước khi chọn.

**Rollback.** Farm bật lại chấp nhận `farmverse-api`.
