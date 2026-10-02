ALTER TABLE ord_outbox ADD COLUMN IF NOT EXISTS aggregate_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE ord_outbox ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE ord_outbox ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE ord_outbox ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE ord_outbox ADD COLUMN IF NOT EXISTS published_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE ord_outbox ADD COLUMN IF NOT EXISTS last_error_code VARCHAR(120);
ALTER TABLE ord_outbox ADD COLUMN IF NOT EXISTS farm_id VARCHAR(100);
ALTER TABLE ord_outbox ADD COLUMN IF NOT EXISTS batch_id VARCHAR(100);
ALTER TABLE ord_outbox ADD COLUMN IF NOT EXISTS order_status VARCHAR(40);
ALTER TABLE ord_outbox ADD COLUMN IF NOT EXISTS currency_code VARCHAR(3);
ALTER TABLE ord_outbox ADD COLUMN IF NOT EXISTS total_minor BIGINT;
ALTER TABLE ord_outbox ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(500);
ALTER TABLE ord_outbox ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 0;

UPDATE ord_outbox o SET
    next_attempt_at = to_timestamp(o.created_at / 1000.0),
    farm_id = r.farm_id,
    batch_id = r.batch_id,
    order_status = r.status,
    currency_code = r.currency_code,
    total_minor = r.total_minor,
    failure_reason = r.failure_reason,
    aggregate_version = r.row_version
FROM ord_order r
WHERE r.order_id = o.aggregate_id AND r.tenant_id = o.tenant_id
  AND (o.next_attempt_at IS NULL OR o.farm_id IS NULL OR o.order_status IS NULL);

ALTER TABLE ord_outbox ALTER COLUMN next_attempt_at SET NOT NULL;
ALTER TABLE ord_outbox ALTER COLUMN farm_id SET NOT NULL;
ALTER TABLE ord_outbox ALTER COLUMN order_status SET NOT NULL;
ALTER TABLE ord_outbox ALTER COLUMN currency_code SET NOT NULL;
ALTER TABLE ord_outbox ALTER COLUMN total_minor SET NOT NULL;

DROP INDEX IF EXISTS ix_ord_outbox_status;
CREATE INDEX IF NOT EXISTS ix_ord_outbox_delivery
    ON ord_outbox (status, next_attempt_at, created_at);
CREATE INDEX IF NOT EXISTS ix_ord_outbox_aggregate
    ON ord_outbox (tenant_id, aggregate_id, aggregate_version);
