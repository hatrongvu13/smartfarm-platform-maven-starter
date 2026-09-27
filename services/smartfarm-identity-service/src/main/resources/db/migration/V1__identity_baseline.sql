-- Identity service baseline schema (V1). Matches the JPA entities exactly (sf_user, sf_role,
-- sf_user_role, sf_role_permission, sf_refresh). Timestamps are epoch millis (BIGINT).
-- IF NOT EXISTS makes this safe to apply over a DB already populated by the earlier ddl-auto phase.

CREATE TABLE IF NOT EXISTS sf_user (
    id               VARCHAR(36)  NOT NULL,
    tenant_id        VARCHAR(100) NOT NULL,
    email            VARCHAR(254) NOT NULL,
    password_hash    VARCHAR(120) NOT NULL,
    enabled          BOOLEAN      NOT NULL,
    failed_attempts  INTEGER      NOT NULL,
    locked_until     BIGINT,
    CONSTRAINT pk_sf_user PRIMARY KEY (id),
    CONSTRAINT uq_user_tenant_email UNIQUE (tenant_id, email)
);

CREATE TABLE IF NOT EXISTS sf_role (
    code  VARCHAR(40)  NOT NULL,
    CONSTRAINT pk_sf_role PRIMARY KEY (code)
);

CREATE TABLE IF NOT EXISTS sf_user_role (
    user_id    VARCHAR(36)  NOT NULL,
    role_code  VARCHAR(40)  NOT NULL,
    CONSTRAINT pk_sf_user_role PRIMARY KEY (user_id, role_code)
);

CREATE TABLE IF NOT EXISTS sf_role_permission (
    role_code        VARCHAR(40)  NOT NULL,
    permission_code  VARCHAR(80)  NOT NULL,
    CONSTRAINT pk_sf_role_permission PRIMARY KEY (role_code, permission_code)
);

CREATE TABLE IF NOT EXISTS sf_refresh (
    id          VARCHAR(36)  NOT NULL,
    user_id     VARCHAR(36)  NOT NULL,
    family_id   VARCHAR(36)  NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL,
    expires_at  BIGINT       NOT NULL,
    revoked     BOOLEAN      NOT NULL,
    CONSTRAINT pk_sf_refresh PRIMARY KEY (id),
    CONSTRAINT uq_sf_refresh_token UNIQUE (token_hash)
);
CREATE INDEX IF NOT EXISTS ix_refresh_family ON sf_refresh (family_id);
CREATE INDEX IF NOT EXISTS ix_refresh_user   ON sf_refresh (user_id);
