-- Livestock service baseline schema (V1). Matches the JPA entities exactly
-- (sf_task, sf_animal, sf_task_schedule, sf_outbox). Timestamps are epoch millis (BIGINT).
-- IF NOT EXISTS makes this safe over a DB already populated by the earlier ddl-auto phase.

CREATE TABLE IF NOT EXISTS sf_task (
    id                           VARCHAR(36)  NOT NULL,
    tenant_id                    VARCHAR(100) NOT NULL,
    farm_id                      VARCHAR(100) NOT NULL,
    title                        VARCHAR(200) NOT NULL,
    assignee_id                  VARCHAR(100) NOT NULL,
    status                       VARCHAR(32)  NOT NULL,
    task_type                    VARCHAR(40),
    created_at                   BIGINT       NOT NULL,
    due_at                       BIGINT,
    assigned_at                  BIGINT,
    accept_deadline_at           BIGINT,
    accepted_at                  BIGINT,
    report_due_at                BIGINT,
    reported_at                  BIGINT,
    completed_at                 BIGINT,
    cancel_reason                VARCHAR(500),
    accept_overdue_notified_at   BIGINT,
    report_overdue_notified_at   BIGINT,
    idempotency_key              VARCHAR(128) NOT NULL,
    CONSTRAINT pk_sf_task PRIMARY KEY (id),
    CONSTRAINT uq_task_idempotency UNIQUE (tenant_id, idempotency_key)
);
CREATE INDEX IF NOT EXISTS ix_task_tenant_farm ON sf_task (tenant_id, farm_id);

CREATE TABLE IF NOT EXISTS sf_animal (
    animal_id   VARCHAR(36)  NOT NULL,
    tenant_id   VARCHAR(100) NOT NULL,
    farm_id     VARCHAR(100) NOT NULL,
    barn_id     VARCHAR(100),
    batch_id    VARCHAR(100),
    tag_code    VARCHAR(100) NOT NULL,
    species     VARCHAR(80),
    birth_date  BIGINT,
    status      VARCHAR(40)  NOT NULL,
    created_at  BIGINT       NOT NULL,
    CONSTRAINT pk_sf_animal PRIMARY KEY (animal_id),
    CONSTRAINT uq_animal_tag UNIQUE (tenant_id, farm_id, tag_code)
);
CREATE INDEX IF NOT EXISTS ix_animal_farm_batch ON sf_animal (tenant_id, farm_id, batch_id);

CREATE TABLE IF NOT EXISTS sf_task_schedule (
    schedule_id      VARCHAR(36)  NOT NULL,
    tenant_id        VARCHAR(100) NOT NULL,
    farm_id          VARCHAR(100) NOT NULL,
    title            VARCHAR(200) NOT NULL,
    task_type        VARCHAR(40),
    cron_expression  VARCHAR(120) NOT NULL,
    time_zone        VARCHAR(60)  NOT NULL,
    assignee_id      VARCHAR(100),
    enabled          BOOLEAN      NOT NULL,
    next_run_at      BIGINT,
    created_at       BIGINT       NOT NULL,
    CONSTRAINT pk_sf_task_schedule PRIMARY KEY (schedule_id)
);
CREATE INDEX IF NOT EXISTS ix_sched_farm ON sf_task_schedule (tenant_id, farm_id);
CREATE INDEX IF NOT EXISTS ix_sched_due  ON sf_task_schedule (enabled, next_run_at);

CREATE TABLE IF NOT EXISTS sf_outbox (
    event_id        VARCHAR(36)  NOT NULL,
    tenant_id       VARCHAR(100) NOT NULL,
    aggregate_id    VARCHAR(36)  NOT NULL,
    event_type      VARCHAR(80)  NOT NULL,
    correlation_id  VARCHAR(128),
    created_at      BIGINT       NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    CONSTRAINT pk_sf_outbox PRIMARY KEY (event_id)
);
CREATE INDEX IF NOT EXISTS ix_outbox_status ON sf_outbox (status, created_at);
