-- FarmVerse identity — schema gốc (PostgreSQL). Database riêng của identity-service.
-- Dự án học tập: không giữ lịch sử migration. Đổi schema = sửa file này rồi tạo lại database.

CREATE TABLE tenant (
    id          varchar(36)  PRIMARY KEY,
    code        varchar(100) NOT NULL UNIQUE,
    name        varchar(200) NOT NULL,
    enabled     boolean      NOT NULL DEFAULT true,
    created_at  timestamptz  NOT NULL,
    updated_at  timestamptz  NOT NULL,
    version     bigint       NOT NULL DEFAULT 0
);

CREATE TABLE user_account (
    id               varchar(36)  PRIMARY KEY,
    email            varchar(254) NOT NULL,
    normalized_email varchar(254) NOT NULL UNIQUE,
    password_hash    varchar(255) NOT NULL,
    display_name     varchar(150) NOT NULL,
    locale           varchar(20)  NOT NULL,
    time_zone        varchar(50)  NOT NULL,
    enabled          boolean      NOT NULL DEFAULT true,
    failed_attempts  integer      NOT NULL DEFAULT 0,
    locked_until     timestamptz,
    last_login_at    timestamptz,
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    version          bigint       NOT NULL DEFAULT 0
);

-- Một người (account) có thể thuộc nhiều tenant; mỗi lần thuộc là một membership.
CREATE TABLE membership (
    id          varchar(36) PRIMARY KEY,
    tenant_id   varchar(36) NOT NULL REFERENCES tenant(id),
    user_id     varchar(36) NOT NULL REFERENCES user_account(id),
    status      varchar(20) NOT NULL,
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL,
    version     bigint      NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, user_id)
);
CREATE INDEX ix_membership_user ON membership(user_id);

-- Danh mục permission dùng chung toàn nền tảng.
CREATE TABLE permission (
    code          varchar(80)  PRIMARY KEY,
    resource_type varchar(80)  NOT NULL,
    action        varchar(40)  NOT NULL,
    description   varchar(255),
    system_owned  boolean      NOT NULL DEFAULT false
);

-- Role thuộc về một tenant.
CREATE TABLE role (
    id           varchar(36)  PRIMARY KEY,
    tenant_id    varchar(36)  NOT NULL REFERENCES tenant(id),
    code         varchar(40)  NOT NULL,
    name         varchar(120) NOT NULL,
    description  varchar(255),
    system_owned boolean      NOT NULL DEFAULT false,
    created_at   timestamptz  NOT NULL,
    updated_at   timestamptz  NOT NULL,
    version      bigint       NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, code)
);

CREATE TABLE role_permission (
    role_id         varchar(36) NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    permission_code varchar(80) NOT NULL REFERENCES permission(code) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_code)
);

CREATE TABLE membership_role (
    membership_id varchar(36) NOT NULL REFERENCES membership(id) ON DELETE CASCADE,
    role_id       varchar(36) NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    PRIMARY KEY (membership_id, role_id)
);

-- MFA TOTP (RFC 6238). Secret mã hóa AES-GCM; confirmed_at null = đang đăng ký dở.
CREATE TABLE user_totp (
    user_id          varchar(36)   PRIMARY KEY REFERENCES user_account(id) ON DELETE CASCADE,
    secret_cipher    varchar(512)  NOT NULL,
    confirmed_at     timestamptz,
    last_used_step   bigint,
    created_at       timestamptz   NOT NULL,
    updated_at       timestamptz   NOT NULL,
    version          bigint        NOT NULL DEFAULT 0
);
