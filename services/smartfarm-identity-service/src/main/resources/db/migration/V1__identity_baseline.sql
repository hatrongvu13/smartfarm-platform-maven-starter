-- Identity service baseline schema (V1). Matches the JPA entities exactly so a fresh
-- prod database can be created cleanly and Hibernate `ddl-auto: validate` passes.
-- Previously identity had flyway.enabled + ddl-auto: validate but ZERO migrations, so on a
-- fresh prod DB Flyway created nothing and validate failed at boot. This baseline owns the
-- schema instead.
--
-- Conventions (matched to the order/health/finance baselines):
--   String                        -> VARCHAR(@Column length, default 255)
--   long / Long / @Version        -> BIGINT   (@Version columns DEFAULT 0)
--   int / Integer                 -> INTEGER
--   boolean                       -> BOOLEAN
--   Instant                       -> TIMESTAMP WITH TIME ZONE
--   @Enumerated(STRING) enum      -> VARCHAR(@Column length)
--   @Lob byte[]                   -> BYTEA
--   @Id                           -> CONSTRAINT pk_<table> PRIMARY KEY
--   @EmbeddedId                   -> composite PRIMARY KEY
--   @UniqueConstraint             -> named UNIQUE
--   @Index                        -> CREATE INDEX IF NOT EXISTS
-- Entities extending AuditableEntity contribute: created_at / updated_at
-- (TIMESTAMP WITH TIME ZONE NOT NULL) + entity_version (BIGINT NOT NULL DEFAULT 0).
-- Foreign-key relationships (@ManyToOne/@OneToOne @ForeignKey) are intentionally NOT emitted
-- as DB constraints, matching the sibling baselines -- Hibernate `validate` checks columns,
-- types and PK/unique, not FK constraints.

-- ============================================================================
-- Tenancy & accounts
-- ============================================================================

-- TenantEntity -> sf_tenant  (extends AuditableEntity)
CREATE TABLE IF NOT EXISTS sf_tenant (
    id              VARCHAR(36)              NOT NULL,
    code            VARCHAR(100)             NOT NULL,
    name            VARCHAR(200)             NOT NULL,
    enabled         BOOLEAN                  NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    entity_version  BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT pk_sf_tenant PRIMARY KEY (id),
    CONSTRAINT uq_tenant_code UNIQUE (code)
);

-- UserAccountEntity -> sf_user_account  (extends AuditableEntity)
CREATE TABLE IF NOT EXISTS sf_user_account (
    id                    VARCHAR(36)              NOT NULL,
    email                 VARCHAR(254)             NOT NULL,
    normalized_email      VARCHAR(254)             NOT NULL,
    password_hash         VARCHAR(255)             NOT NULL,
    enabled               BOOLEAN                  NOT NULL,
    failed_attempts       INTEGER                  NOT NULL,
    locked_until          TIMESTAMP WITH TIME ZONE,
    email_verified_at     TIMESTAMP WITH TIME ZONE,
    phone_verified_at     TIMESTAMP WITH TIME ZONE,
    last_login_at         TIMESTAMP WITH TIME ZONE,
    password_changed_at   TIMESTAMP WITH TIME ZONE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    entity_version        BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT pk_sf_user_account PRIMARY KEY (id),
    CONSTRAINT uq_user_account_normalized_email UNIQUE (normalized_email)
);

-- UserProfileEntity -> sf_user_profile  (extends AuditableEntity; @MapsId shares user_id with sf_user_account)
CREATE TABLE IF NOT EXISTS sf_user_profile (
    user_id         VARCHAR(36)              NOT NULL,
    display_name    VARCHAR(150)             NOT NULL,
    first_name      VARCHAR(100),
    last_name       VARCHAR(100),
    phone_number    VARCHAR(30),
    avatar_url      VARCHAR(500),
    locale          VARCHAR(20)              NOT NULL,
    time_zone       VARCHAR(50)              NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    entity_version  BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT pk_sf_user_profile PRIMARY KEY (user_id)
);

-- TenantMembershipEntity -> sf_tenant_membership  (extends AuditableEntity)
CREATE TABLE IF NOT EXISTS sf_tenant_membership (
    id              VARCHAR(36)              NOT NULL,
    tenant_id       VARCHAR(36)              NOT NULL,
    user_id         VARCHAR(36)              NOT NULL,
    status          VARCHAR(20)              NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    entity_version  BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT pk_sf_tenant_membership PRIMARY KEY (id),
    CONSTRAINT uq_membership_tenant_user UNIQUE (tenant_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_membership_user ON sf_tenant_membership (user_id);
CREATE INDEX IF NOT EXISTS idx_membership_tenant_status ON sf_tenant_membership (tenant_id, status);

-- ============================================================================
-- Authorization (roles, permissions, grants)
-- ============================================================================

-- PermissionEntity -> sf_permission  (extends AuditableEntity; @Id is the natural key `code`)
CREATE TABLE IF NOT EXISTS sf_permission (
    code            VARCHAR(80)              NOT NULL,
    resource_type   VARCHAR(80)              NOT NULL,
    action          VARCHAR(40)              NOT NULL,
    description     VARCHAR(255),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    entity_version  BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT pk_sf_permission PRIMARY KEY (code),
    CONSTRAINT uq_permission_resource_action UNIQUE (resource_type, action)
);

-- RoleEntity -> sf_role  (extends AuditableEntity)
CREATE TABLE IF NOT EXISTS sf_role (
    id              VARCHAR(36)              NOT NULL,
    tenant_id       VARCHAR(36)              NOT NULL,
    code            VARCHAR(40)              NOT NULL,
    name            VARCHAR(120)             NOT NULL,
    system_role     BOOLEAN                  NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    entity_version  BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT pk_sf_role PRIMARY KEY (id),
    CONSTRAINT uq_role_tenant_code UNIQUE (tenant_id, code)
);
CREATE INDEX IF NOT EXISTS idx_role_tenant ON sf_role (tenant_id);

-- RolePermissionEntity -> sf_role_permission  (@EmbeddedId RolePermissionId -> composite PK)
CREATE TABLE IF NOT EXISTS sf_role_permission (
    role_id          VARCHAR(36)  NOT NULL,
    permission_code  VARCHAR(80)  NOT NULL,
    CONSTRAINT pk_sf_role_permission PRIMARY KEY (role_id, permission_code)
);

-- MembershipRoleEntity -> sf_membership_role  (@EmbeddedId MembershipRoleId -> composite PK)
CREATE TABLE IF NOT EXISTS sf_membership_role (
    membership_id  VARCHAR(36)              NOT NULL,
    role_id        VARCHAR(36)              NOT NULL,
    granted_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    granted_by     VARCHAR(36)              NOT NULL,
    CONSTRAINT pk_sf_membership_role PRIMARY KEY (membership_id, role_id)
);

-- MembershipFarmEntity -> sf_membership_farm  (@EmbeddedId MembershipFarmId -> composite PK)
CREATE TABLE IF NOT EXISTS sf_membership_farm (
    membership_id  VARCHAR(36)  NOT NULL,
    farm_id        VARCHAR(36)  NOT NULL,
    CONSTRAINT pk_sf_membership_farm PRIMARY KEY (membership_id, farm_id)
);

-- ============================================================================
-- Tokens
-- ============================================================================

-- RefreshEntity -> sf_refresh  (plain @Entity; `id` and `revoked` use Hibernate-default
-- column names since they carry no @Column(name=...): id -> id, revoked -> revoked)
CREATE TABLE IF NOT EXISTS sf_refresh (
    id          VARCHAR(36)  NOT NULL,
    user_id     VARCHAR(36)  NOT NULL,
    tenant_id   VARCHAR(36)  NOT NULL,
    audience    VARCHAR(100) NOT NULL,
    family_id   VARCHAR(36)  NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL,
    expires_at  BIGINT       NOT NULL,
    revoked     BOOLEAN      NOT NULL,
    CONSTRAINT pk_sf_refresh PRIMARY KEY (id),
    CONSTRAINT uq_sf_refresh_token_hash UNIQUE (token_hash)
);
CREATE INDEX IF NOT EXISTS ix_refresh_family ON sf_refresh (family_id);
CREATE INDEX IF NOT EXISTS ix_refresh_user ON sf_refresh (user_id);
CREATE INDEX IF NOT EXISTS ix_refresh_user_tenant ON sf_refresh (user_id, tenant_id);

-- ============================================================================
-- MFA
-- ============================================================================

-- UserAuthenticatorEntity -> sf_user_authenticator  (extends AuditableEntity)
CREATE TABLE IF NOT EXISTS sf_user_authenticator (
    id                       VARCHAR(36)              NOT NULL,
    user_id                  VARCHAR(36)              NOT NULL,
    authenticator_type       VARCHAR(20)              NOT NULL,
    status                   VARCHAR(20)              NOT NULL,
    display_name             VARCHAR(100)             NOT NULL,
    secret_ciphertext        VARCHAR(2048),
    verified_at              TIMESTAMP WITH TIME ZONE,
    last_used_at             TIMESTAMP WITH TIME ZONE,
    last_accepted_time_step  BIGINT,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    entity_version           BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT pk_sf_user_authenticator PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS ix_authenticator_user_status ON sf_user_authenticator (user_id, status);

-- RecoveryCodeEntity -> sf_recovery_code  (extends AuditableEntity)
CREATE TABLE IF NOT EXISTS sf_recovery_code (
    id                VARCHAR(36)              NOT NULL,
    authenticator_id  VARCHAR(36)              NOT NULL,
    code_hash         VARCHAR(64)              NOT NULL,
    used_at           TIMESTAMP WITH TIME ZONE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    entity_version    BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT pk_sf_recovery_code PRIMARY KEY (id),
    CONSTRAINT uq_sf_recovery_code_hash UNIQUE (code_hash)
);
CREATE INDEX IF NOT EXISTS ix_recovery_authenticator ON sf_recovery_code (authenticator_id);

-- MfaChallengeEntity -> sf_mfa_challenge  (extends AuditableEntity)
CREATE TABLE IF NOT EXISTS sf_mfa_challenge (
    id              VARCHAR(36)              NOT NULL,
    user_id         VARCHAR(36)              NOT NULL,
    tenant_id       VARCHAR(36)              NOT NULL,
    purpose         VARCHAR(30)              NOT NULL,
    status          VARCHAR(20)              NOT NULL,
    challenge_hash  VARCHAR(64)              NOT NULL,
    attempt_count   INTEGER                  NOT NULL,
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    verified_at     TIMESTAMP WITH TIME ZONE,
    consumed_at     TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    entity_version  BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT pk_sf_mfa_challenge PRIMARY KEY (id),
    CONSTRAINT uq_sf_mfa_challenge_hash UNIQUE (challenge_hash)
);
CREATE INDEX IF NOT EXISTS ix_mfa_challenge_user_status ON sf_mfa_challenge (user_id, status);
CREATE INDEX IF NOT EXISTS ix_mfa_challenge_expiry ON sf_mfa_challenge (expires_at);

-- ============================================================================
-- Messaging (MQTT command inbox + domain-event outbox)
-- ============================================================================

-- IdentityMqttInboxEntity -> sf_identity_mqtt_inbox  (plain @Entity; @Version -> row_version)
CREATE TABLE IF NOT EXISTS sf_identity_mqtt_inbox (
    command_id       VARCHAR(100)             NOT NULL,
    tenant_id        VARCHAR(100),
    actor_id         VARCHAR(100),
    correlation_id   VARCHAR(128),
    command_type     VARCHAR(120),
    schema_version   INTEGER,
    topic            VARCHAR(300)             NOT NULL,
    payload          BYTEA,
    status           VARCHAR(20)              NOT NULL,
    rejection_code   VARCHAR(120),
    received_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    issued_at        TIMESTAMP WITH TIME ZONE,
    processed_at     TIMESTAMP WITH TIME ZONE,
    next_attempt_at  TIMESTAMP WITH TIME ZONE,
    claimed_at       TIMESTAMP WITH TIME ZONE,
    attempt_count    INTEGER                  NOT NULL,
    last_error_code  VARCHAR(120),
    row_version      BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT pk_sf_identity_mqtt_inbox PRIMARY KEY (command_id)
);
CREATE INDEX IF NOT EXISTS ix_identity_inbox_status_received
    ON sf_identity_mqtt_inbox (status, received_at);
CREATE INDEX IF NOT EXISTS ix_identity_inbox_tenant_type
    ON sf_identity_mqtt_inbox (tenant_id, command_type, received_at);

-- IdentityOutboxEntity -> sf_identity_outbox  (plain @Entity; @Version -> row_version)
CREATE TABLE IF NOT EXISTS sf_identity_outbox (
    event_id           VARCHAR(36)              NOT NULL,
    tenant_id          VARCHAR(100),
    actor_id           VARCHAR(100),
    correlation_id     VARCHAR(128),
    causation_id       VARCHAR(128),
    event_type         VARCHAR(120)             NOT NULL,
    schema_version     INTEGER                  NOT NULL,
    aggregate_type     VARCHAR(80)              NOT NULL,
    aggregate_id       VARCHAR(100)             NOT NULL,
    aggregate_version  BIGINT                   NOT NULL,
    topic              VARCHAR(300)             NOT NULL,
    payload            BYTEA                    NOT NULL,
    status             VARCHAR(20)              NOT NULL,
    occurred_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    next_attempt_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    attempt_count      INTEGER                  NOT NULL,
    claimed_at         TIMESTAMP WITH TIME ZONE,
    published_at       TIMESTAMP WITH TIME ZONE,
    last_error_code    VARCHAR(120),
    row_version        BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT pk_sf_identity_outbox PRIMARY KEY (event_id)
);
CREATE INDEX IF NOT EXISTS ix_identity_outbox_delivery
    ON sf_identity_outbox (status, next_attempt_at, occurred_at);
CREATE INDEX IF NOT EXISTS ix_identity_outbox_aggregate
    ON sf_identity_outbox (tenant_id, aggregate_type, aggregate_id, aggregate_version);
