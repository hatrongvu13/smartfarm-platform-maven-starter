CREATE TABLE IF NOT EXISTS ord_saga_recovery_audit (
    audit_id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    saga_id VARCHAR(36) NOT NULL,
    step_key VARCHAR(160),
    actor_id VARCHAR(100) NOT NULL,
    action VARCHAR(30) NOT NULL,
    previous_status VARCHAR(30) NOT NULL,
    new_status VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_ord_saga_recovery_audit PRIMARY KEY (audit_id)
);
CREATE INDEX IF NOT EXISTS ix_ord_saga_recovery_saga
    ON ord_saga_recovery_audit (saga_id, occurred_at);
CREATE INDEX IF NOT EXISTS ix_ord_saga_recovery_tenant
    ON ord_saga_recovery_audit (tenant_id, occurred_at);
