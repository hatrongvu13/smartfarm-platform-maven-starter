ALTER TABLE ord_outbox ADD COLUMN IF NOT EXISTS actor_id VARCHAR(100);
ALTER TABLE ord_outbox ADD COLUMN IF NOT EXISTS saga_id VARCHAR(36);
ALTER TABLE ord_outbox ADD COLUMN IF NOT EXISTS step_key VARCHAR(160);
ALTER TABLE ord_outbox ADD COLUMN IF NOT EXISTS previous_status VARCHAR(40);
ALTER TABLE ord_outbox ADD COLUMN IF NOT EXISTS new_status VARCHAR(40);
ALTER TABLE ord_outbox ADD COLUMN IF NOT EXISTS reason_code VARCHAR(120);
ALTER TABLE ord_outbox ADD COLUMN IF NOT EXISTS event_reason VARCHAR(500);

CREATE INDEX IF NOT EXISTS ix_ord_outbox_saga
    ON ord_outbox (tenant_id, saga_id, created_at);
CREATE INDEX IF NOT EXISTS ix_ord_outbox_event_type
    ON ord_outbox (tenant_id, event_type, created_at);
