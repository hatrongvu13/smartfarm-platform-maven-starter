-- I1: refresh token đã bị xoay (chỉ lưu hash SHA-256), để phát hiện dùng lại (RFC 9700 §4.14.2).
-- Xem RefreshTokenReuseDetection. Xóa theo authorization (ON DELETE CASCADE).
CREATE TABLE oauth2_rotated_refresh_token (
    token_hash       CHAR(64)     PRIMARY KEY,
    authorization_id VARCHAR(100) NOT NULL REFERENCES oauth2_authorization (id) ON DELETE CASCADE,
    rotated_at       TIMESTAMPTZ  NOT NULL,
    expires_at       TIMESTAMPTZ  NOT NULL
);

CREATE INDEX oauth2_rotated_refresh_token_authorization_idx ON oauth2_rotated_refresh_token (authorization_id);
