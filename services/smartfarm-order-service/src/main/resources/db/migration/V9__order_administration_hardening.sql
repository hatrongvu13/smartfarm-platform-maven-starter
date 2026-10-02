ALTER TABLE ord_saga_recovery_audit
    ALTER COLUMN action TYPE VARCHAR(40);

CREATE INDEX IF NOT EXISTS ix_ord_outbox_tenant_status_created
    ON ord_outbox (tenant_id, status, created_at, event_id);
