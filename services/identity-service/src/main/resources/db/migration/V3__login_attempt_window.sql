-- M8: bộ đếm giới hạn đăng nhập theo IP, dùng chung giữa mọi instance identity (JdbcLoginAttemptCounter).
-- Mỗi dòng là một cửa sổ cố định của một khóa ("login:<ip>"). Dòng hết hạn bị xóa định kỳ, nên bảng nhỏ.
-- Quyền DML cho identity_app đến từ ALTER DEFAULT PRIVILEGES trong infra/postgres-init.sh.
CREATE TABLE login_attempt_window (
    bucket   VARCHAR(100) PRIMARY KEY,
    attempts INTEGER      NOT NULL,
    reset_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX login_attempt_window_reset_at_idx ON login_attempt_window (reset_at);
