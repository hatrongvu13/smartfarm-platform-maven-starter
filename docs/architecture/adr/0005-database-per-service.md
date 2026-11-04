# ADR-0005: Database per service

**Trạng thái:** Đã chốt (database 2026-10-27; vai trò riêng M4, 2026-11-04).

**Bối cảnh.** Hai service chung bảng thì một service đổi schema làm hỏng service kia, và một service bị chiếm đọc được dữ liệu của service kia.

**Quyết định.** Mỗi service một database (`farmverse_identity`, `farmverse_farm`), Flyway riêng. Mục tiêu: mỗi service có user owner (chạy migration) và user app (chỉ DML), không user nào connect được database của service khác.

**Hệ quả.** Không join chéo service; dữ liệu cần từ service khác lấy qua API (hoặc gRPC, đang chờ duyệt).

**Đã làm (M4).** `infra/postgres-init.sh` (chạy lại được nhiều lần) tạo `<svc>_owner` / `<svc>_app`, thu hồi `CONNECT` của `PUBLIC`, cấp default privileges để bảng Flyway tạo sau tự có quyền DML cho app, và chuyển quyền sở hữu bảng cũ do `postgres` tạo. Ứng dụng chạy bằng `<svc>_app`, Flyway bằng `<svc>_owner` (`spring.flyway.user`).

**Còn lại.** Ứng dụng vẫn giữ mật khẩu owner để chạy Flyway lúc khởi động. Muốn bỏ thì tách migration thành job riêng.

**Bằng chứng.** [`infra/postgres-init.sh`](../../../infra/postgres-init.sh), [`DatabaseRolesIT`](../../../services/farm-service/src/test/java/com/htv/farmverse/farm/DatabaseRolesIT.java) (không vào chéo được, app không DDL được, chạy lại script nhận bảng cũ; đối chứng: bỏ `REVOKE` thì đỏ), `PostgresFlowsIT` và `FarmPostgresIT` chạy bằng vai trò app.
