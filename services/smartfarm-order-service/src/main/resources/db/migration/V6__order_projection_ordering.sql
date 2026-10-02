ALTER TABLE ord_order_view ADD COLUMN IF NOT EXISTS last_aggregate_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE ord_order_view ADD COLUMN IF NOT EXISTS last_event_id VARCHAR(100);
ALTER TABLE ord_order_view ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 0;

UPDATE ord_order_view v SET
    last_aggregate_version = r.row_version,
    last_event_at = GREATEST(v.last_event_at, r.updated_at)
FROM ord_order r
WHERE r.order_id = v.order_id AND r.tenant_id = v.tenant_id
  AND v.last_aggregate_version = 0;

CREATE TABLE IF NOT EXISTS ord_projection_inbox (
    event_id VARCHAR(100) NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(36) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    payload BYTEA NOT NULL,
    status VARCHAR(20) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE,
    last_error_code VARCHAR(120),
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_ord_projection_inbox PRIMARY KEY (event_id),
    CONSTRAINT uq_ord_projection_version UNIQUE (tenant_id, aggregate_id, aggregate_version)
);
CREATE INDEX IF NOT EXISTS ix_ord_projection_gap
    ON ord_projection_inbox (tenant_id, aggregate_id, aggregate_version, status);
CREATE INDEX IF NOT EXISTS ix_ord_projection_status
    ON ord_projection_inbox (status, received_at);
