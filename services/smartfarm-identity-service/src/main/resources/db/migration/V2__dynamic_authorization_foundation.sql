-- Dynamic authorization foundation.
-- Version rows are authoritative monotonic watermarks used by PDP responses,
-- delegated tokens, local decision caches and MQTT invalidation consumers.

CREATE TABLE IF NOT EXISTS sf_authorization_tenant_version (
    tenant_id       VARCHAR(36)              NOT NULL,
    version_value   BIGINT                   NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    row_version     BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT pk_sf_authorization_tenant_version PRIMARY KEY (tenant_id),
    CONSTRAINT ck_sf_authorization_tenant_version_non_negative
        CHECK (version_value >= 0)
);

CREATE TABLE IF NOT EXISTS sf_authorization_subject_version (
    membership_id   VARCHAR(36)              NOT NULL,
    tenant_id       VARCHAR(36)              NOT NULL,
    subject_id      VARCHAR(36)              NOT NULL,
    version_value   BIGINT                   NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    row_version     BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT pk_sf_authorization_subject_version PRIMARY KEY (membership_id),
    CONSTRAINT uq_sf_authorization_subject_version_lookup
        UNIQUE (tenant_id, subject_id),
    CONSTRAINT ck_sf_authorization_subject_version_non_negative
        CHECK (version_value >= 0)
);
CREATE INDEX IF NOT EXISTS ix_sf_authorization_subject_version_tenant
    ON sf_authorization_subject_version (tenant_id, version_value);

CREATE TABLE IF NOT EXISTS sf_permission_registration (
    service_id          VARCHAR(100)             NOT NULL,
    permission_code     VARCHAR(80)              NOT NULL,
    resource_type       VARCHAR(80)              NOT NULL,
    action_name         VARCHAR(40)              NOT NULL,
    description         VARCHAR(255),
    registration_hash   VARCHAR(64)              NOT NULL,
    active              BOOLEAN                  NOT NULL DEFAULT TRUE,
    first_seen_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    retired_at          TIMESTAMP WITH TIME ZONE,
    row_version         BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT pk_sf_permission_registration
        PRIMARY KEY (service_id, permission_code),
    CONSTRAINT ck_sf_permission_registration_state
        CHECK ((active = TRUE AND retired_at IS NULL)
            OR (active = FALSE AND retired_at IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS ix_sf_permission_registration_permission
    ON sf_permission_registration (permission_code, active);
CREATE INDEX IF NOT EXISTS ix_sf_permission_registration_service
    ON sf_permission_registration (service_id, active);

-- Existing memberships start at version 0. Subsequent authorization mutations
-- must lock and increment the matching row in the same transaction as the mutation.
INSERT INTO sf_authorization_subject_version (
    membership_id, tenant_id, subject_id, version_value, updated_at, row_version
)
SELECT id, tenant_id, user_id, 0, CURRENT_TIMESTAMP, 0
FROM sf_tenant_membership
ON CONFLICT (membership_id) DO NOTHING;

INSERT INTO sf_authorization_tenant_version (
    tenant_id, version_value, updated_at, row_version
)
SELECT id, 0, CURRENT_TIMESTAMP, 0
FROM sf_tenant
ON CONFLICT (tenant_id) DO NOTHING;
