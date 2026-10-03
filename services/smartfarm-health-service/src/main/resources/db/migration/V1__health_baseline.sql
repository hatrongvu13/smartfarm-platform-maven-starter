-- Health service baseline schema (V1). Matches the JPA entities exactly
-- (ObservationEntity -> h_observation, VaccinationEntity -> h_vaccination,
-- HealthOutboxEntity -> h_outbox). Payloads are serialized protobuf (BYTEA).
-- Previously the schema was Hibernate-generated (ddl-auto); Flyway now owns it so a
-- fresh database can be created cleanly and prod can run ddl-auto: validate.

CREATE TABLE IF NOT EXISTS h_observation (
    id               VARCHAR(36)  NOT NULL,
    tenant_id        VARCHAR(100) NOT NULL,
    animal_id        VARCHAR(100) NOT NULL,
    farm_id          VARCHAR(100) NOT NULL,
    observed_at      BIGINT       NOT NULL,
    idempotency_key  VARCHAR(128) NOT NULL,
    payload          BYTEA        NOT NULL,
    CONSTRAINT pk_h_observation PRIMARY KEY (id),
    CONSTRAINT uq_h_obs_idem UNIQUE (tenant_id, idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_h_obs_tenant_animal
    ON h_observation (tenant_id, animal_id, observed_at);

CREATE TABLE IF NOT EXISTS h_vaccination (
    id               VARCHAR(36)  NOT NULL,
    tenant_id        VARCHAR(100) NOT NULL,
    animal_id        VARCHAR(100) NOT NULL,
    administered_at  BIGINT       NOT NULL,
    idempotency_key  VARCHAR(128) NOT NULL,
    payload          BYTEA        NOT NULL,
    CONSTRAINT pk_h_vaccination PRIMARY KEY (id),
    CONSTRAINT uq_h_vax_idem UNIQUE (tenant_id, idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_h_vax_tenant_animal
    ON h_vaccination (tenant_id, animal_id, administered_at);

CREATE TABLE IF NOT EXISTS h_outbox (
    event_id      VARCHAR(36)  NOT NULL,
    tenant_id     VARCHAR(100) NOT NULL,
    farm_id       VARCHAR(100) NOT NULL,
    aggregate_id  VARCHAR(36)  NOT NULL,
    event_type    VARCHAR(80)  NOT NULL,
    payload       BYTEA        NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    CONSTRAINT pk_h_outbox PRIMARY KEY (event_id)
);
CREATE INDEX IF NOT EXISTS ix_h_outbox_status ON h_outbox (status);
