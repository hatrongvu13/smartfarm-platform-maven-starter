-- Reporting service baseline schema (V1). Matches ExportJobEntity -> rpt_export_job.
-- Previously Hibernate-generated (ddl-auto); Flyway now owns it so a fresh database can
-- be created cleanly and prod can run ddl-auto: validate (prod previously had no schema
-- management at all and inherited the dev 'update' default).

CREATE TABLE IF NOT EXISTS rpt_export_job (
    job_id           VARCHAR(36)  NOT NULL,
    tenant_id        VARCHAR(100) NOT NULL,
    farm_id          VARCHAR(100) NOT NULL,
    report_type      VARCHAR(40)  NOT NULL,
    report_format    VARCHAR(40)  NOT NULL,
    status           VARCHAR(40)  NOT NULL,
    period_from      BIGINT,
    period_to        BIGINT,
    template_id      VARCHAR(100),
    file_path        VARCHAR(500),
    content_type     VARCHAR(100),
    error_code       VARCHAR(100),
    created_at       BIGINT       NOT NULL,
    completed_at     BIGINT,
    idempotency_key  VARCHAR(128) NOT NULL,
    CONSTRAINT pk_rpt_export_job PRIMARY KEY (job_id),
    CONSTRAINT uq_rpt_idem UNIQUE (tenant_id, idempotency_key)
);
CREATE INDEX IF NOT EXISTS ix_rpt_status ON rpt_export_job (status, created_at);
