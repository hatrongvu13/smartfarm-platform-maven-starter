CREATE TABLE IF NOT EXISTS ord_order_event_archive (
    event_id VARCHAR(100) NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(36) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    correlation_id VARCHAR(128),
    created_at BIGINT NOT NULL,
    farm_id VARCHAR(100) NOT NULL,
    batch_id VARCHAR(100),
    order_status VARCHAR(40) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    total_minor BIGINT NOT NULL,
    failure_reason VARCHAR(500),
    CONSTRAINT pk_ord_order_event_archive PRIMARY KEY (event_id),
    CONSTRAINT uq_ord_event_archive_version UNIQUE (tenant_id, aggregate_id, aggregate_version)
);
CREATE INDEX IF NOT EXISTS ix_ord_event_archive_aggregate
    ON ord_order_event_archive (tenant_id, aggregate_id, aggregate_version);

CREATE TABLE IF NOT EXISTS ord_projection_gap (
    gap_id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(36) NOT NULL,
    current_version BIGINT NOT NULL,
    missing_from_version BIGINT NOT NULL,
    missing_to_version BIGINT NOT NULL,
    highest_buffered_version BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    first_detected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_checked_at TIMESTAMP WITH TIME ZONE,
    next_recovery_at TIMESTAMP WITH TIME ZONE NOT NULL,
    claimed_at TIMESTAMP WITH TIME ZONE,
    claim_owner VARCHAR(120),
    last_error_code VARCHAR(120),
    last_error_message VARCHAR(500),
    resolved_at TIMESTAMP WITH TIME ZONE,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_ord_projection_gap PRIMARY KEY (gap_id),
    CONSTRAINT uq_ord_projection_gap_aggregate UNIQUE (tenant_id, aggregate_id)
);
CREATE INDEX IF NOT EXISTS ix_ord_projection_gap_claim
    ON ord_projection_gap (status, next_recovery_at, first_detected_at);
CREATE INDEX IF NOT EXISTS ix_ord_projection_gap_stale
    ON ord_projection_gap (status, claimed_at);

CREATE TABLE IF NOT EXISTS ord_projection_recovery_audit (
    audit_id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(36) NOT NULL,
    gap_id VARCHAR(36) NOT NULL,
    aggregate_version BIGINT,
    event_id VARCHAR(100),
    actor_id VARCHAR(120) NOT NULL,
    action VARCHAR(40) NOT NULL,
    previous_status VARCHAR(30) NOT NULL,
    new_status VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_ord_projection_recovery_audit PRIMARY KEY (audit_id)
);
CREATE INDEX IF NOT EXISTS ix_ord_projection_recovery_gap
    ON ord_projection_recovery_audit (gap_id, occurred_at);
CREATE INDEX IF NOT EXISTS ix_ord_projection_recovery_aggregate
    ON ord_projection_recovery_audit (tenant_id, aggregate_id, occurred_at);

-- Backfill only immutable Phase 3A.7 outbox snapshots. Do not reconstruct history from ord_order.
INSERT INTO ord_order_event_archive (
    event_id, tenant_id, aggregate_id, aggregate_version, event_type, correlation_id,
    created_at, farm_id, batch_id, order_status, currency_code, total_minor, failure_reason
)
SELECT event_id, tenant_id, aggregate_id, aggregate_version, event_type, correlation_id,
       created_at, farm_id, batch_id, order_status, currency_code, total_minor, failure_reason
FROM ord_outbox
ON CONFLICT DO NOTHING;
