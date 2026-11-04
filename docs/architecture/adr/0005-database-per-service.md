# ADR-0005: Database per service

**Trạng thái:** Đã chốt cho database. User riêng: chưa làm (M4).

**Bối cảnh.** Hai service chung bảng thì một service đổi schema làm hỏng service kia, và một service bị chiếm đọc được dữ liệu của service kia.

**Quyết định.** Mỗi service một database (`farmverse_identity`, `farmverse_farm`), Flyway riêng. Mục tiêu: mỗi service có user owner (chạy migration) và user app (chỉ DML), không user nào connect được database của service khác.

**Hệ quả.** Không join chéo service; dữ liệu cần từ service khác lấy qua API (hoặc gRPC, đang chờ duyệt).

**Hiện trạng còn lệch.** Dev dùng chung superuser `postgres`. Prod đọc `IDENTITY_DB_USER`, `FARM_DB_USER` nhưng repo chưa có script tạo user/quyền.

**Bằng chứng.** [`infra/postgres-init.sql`](../../../infra/postgres-init.sql), `application-dev.yml` / `application-prod.yml` của hai service.
