-- Clean rebuild baseline. PostgreSQL only. No legacy upgrade support.
CREATE TABLE sf_tenant (
  id varchar(36) primary key, code varchar(100) not null unique,
  name varchar(200) not null, enabled boolean not null default true,
  created_at timestamptz not null, updated_at timestamptz not null, entity_version bigint not null default 0
);
CREATE TABLE sf_user_account (
  id varchar(36) primary key, email varchar(254) not null,
  normalized_email varchar(254) not null unique, password_hash varchar(255) not null,
  enabled boolean not null default true, failed_attempts integer not null default 0,
  locked_until timestamptz, email_verified_at timestamptz, phone_verified_at timestamptz,
  last_login_at timestamptz, password_changed_at timestamptz,
  created_at timestamptz not null, updated_at timestamptz not null, entity_version bigint not null default 0
);
CREATE TABLE sf_user_profile (
  user_id varchar(36) primary key references sf_user_account(id) on delete cascade,
  display_name varchar(150) not null, first_name varchar(100), last_name varchar(100),
  phone_number varchar(30), avatar_url varchar(500), country_code varchar(2), address varchar(500),
  locale varchar(20) not null, time_zone varchar(50) not null,
  created_at timestamptz not null, updated_at timestamptz not null, entity_version bigint not null default 0
);
CREATE TABLE sf_tenant_membership (
  id varchar(36) primary key, tenant_id varchar(36) not null references sf_tenant(id),
  user_id varchar(36) not null references sf_user_account(id), status varchar(20) not null,
  created_at timestamptz not null, updated_at timestamptz not null, entity_version bigint not null default 0,
  unique(tenant_id,user_id)
);
CREATE INDEX ix_membership_user ON sf_tenant_membership(user_id);
CREATE INDEX ix_membership_tenant_status ON sf_tenant_membership(tenant_id,status);

-- Global platform-owned permission vocabulary.
CREATE TABLE sf_permission (
  code varchar(80) primary key, resource_type varchar(80) not null, action varchar(40) not null,
  description varchar(255), system_owned boolean not null default false,
  created_at timestamptz not null, updated_at timestamptz not null, entity_version bigint not null default 0,
  unique(resource_type,action)
);
CREATE TABLE sf_role (
  id varchar(36) primary key, tenant_id varchar(36) not null references sf_tenant(id),
  code varchar(40) not null, name varchar(120) not null, description varchar(255),
  role_type varchar(20) not null, system_owned boolean not null default false,
  created_at timestamptz not null, updated_at timestamptz not null, entity_version bigint not null default 0,
  unique(tenant_id,code)
);
CREATE TABLE sf_role_permission (
  role_id varchar(36) not null references sf_role(id) on delete cascade,
  permission_code varchar(80) not null references sf_permission(code),
  primary key(role_id,permission_code)
);
CREATE TABLE sf_membership_role (
  membership_id varchar(36) not null references sf_tenant_membership(id) on delete cascade,
  role_id varchar(36) not null references sf_role(id) on delete cascade,
  granted_at timestamptz not null, granted_by varchar(100) not null,
  primary key(membership_id,role_id)
);
CREATE TABLE sf_membership_farm (
  membership_id varchar(36) not null references sf_tenant_membership(id) on delete cascade,
  farm_id varchar(36) not null, primary key(membership_id,farm_id)
);

CREATE TABLE sf_authorization_tenant_version (
  tenant_id varchar(36) primary key references sf_tenant(id) on delete cascade,
  version_value bigint not null default 0 check(version_value >= 0),
  updated_at timestamptz not null, row_version bigint not null default 0
);
CREATE TABLE sf_authorization_subject_version (
  membership_id varchar(36) primary key references sf_tenant_membership(id) on delete cascade,
  tenant_id varchar(36) not null, subject_id varchar(36) not null,
  version_value bigint not null default 0 check(version_value >= 0),
  updated_at timestamptz not null, row_version bigint not null default 0,
  unique(tenant_id,subject_id)
);

CREATE TABLE sf_refresh_session (
  id varchar(36) primary key, family_id varchar(36) not null unique,
  user_id varchar(36) not null references sf_user_account(id),
  tenant_id varchar(36) not null references sf_tenant(id), audience varchar(100) not null,
  created_at timestamptz not null, last_used_at timestamptz, expires_at timestamptz not null,
  revoked_at timestamptz, revoke_reason varchar(120), row_version bigint not null default 0
);
CREATE INDEX ix_refresh_session_user_tenant ON sf_refresh_session(user_id,tenant_id);
CREATE INDEX ix_refresh_session_expiry ON sf_refresh_session(expires_at);
CREATE TABLE sf_refresh_token (
  id varchar(36) primary key, session_id varchar(36) not null references sf_refresh_session(id) on delete cascade,
  token_hash varchar(64) not null unique, issued_at timestamptz not null, expires_at timestamptz not null,
  consumed_at timestamptz, revoked_at timestamptz, replaced_by_token_id varchar(36), row_version bigint not null default 0
);
CREATE INDEX ix_refresh_token_session ON sf_refresh_token(session_id);
CREATE INDEX ix_refresh_token_expiry ON sf_refresh_token(expires_at);

-- MFA core.
CREATE TABLE sf_user_authenticator (
  id varchar(36) primary key, user_id varchar(36) not null references sf_user_account(id) on delete cascade,
  authenticator_type varchar(20) not null, status varchar(20) not null, display_name varchar(100) not null,
  secret_ciphertext varchar(2048), enrollment_expires_at timestamptz, verified_at timestamptz,
  last_used_at timestamptz, last_accepted_time_step bigint,
  created_at timestamptz not null, updated_at timestamptz not null, entity_version bigint not null default 0
);
CREATE TABLE sf_recovery_code (
  id varchar(36) primary key, authenticator_id varchar(36) not null references sf_user_authenticator(id) on delete cascade,
  code_hash varchar(64) not null unique, used_at timestamptz,
  created_at timestamptz not null, updated_at timestamptz not null, entity_version bigint not null default 0
);
CREATE TABLE sf_mfa_challenge (
  id varchar(36) primary key, user_id varchar(36) not null references sf_user_account(id),
  tenant_id varchar(36) not null references sf_tenant(id), purpose varchar(30) not null,
  status varchar(20) not null, challenge_hash varchar(64) not null unique,
  attempt_count integer not null default 0, expires_at timestamptz not null,
  verified_at timestamptz, consumed_at timestamptz,
  created_at timestamptz not null, updated_at timestamptz not null, entity_version bigint not null default 0
);

-- Outbox is mandatory for lifecycle and invalidation events.
CREATE TABLE sf_identity_outbox (
  event_id varchar(36) primary key, tenant_id varchar(100), actor_id varchar(100),
  correlation_id varchar(128), causation_id varchar(128), event_type varchar(120) not null,
  schema_version integer not null, aggregate_type varchar(80) not null,
  aggregate_id varchar(100) not null, aggregate_version bigint not null,
  topic varchar(300) not null, payload bytea not null, status varchar(20) not null,
  occurred_at timestamptz not null, next_attempt_at timestamptz not null,
  attempt_count integer not null default 0, claimed_at timestamptz, published_at timestamptz,
  last_error_code varchar(120), row_version bigint not null default 0
);
CREATE INDEX ix_outbox_delivery ON sf_identity_outbox(status,next_attempt_at,occurred_at);
