CREATE TABLE IF NOT EXISTS ord_saga (
    saga_id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    actor_id VARCHAR(100),
    correlation_id VARCHAR(128),
    status VARCHAR(30) NOT NULL,
    current_step_key VARCHAR(160),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    claimed_at TIMESTAMP WITH TIME ZONE,
    last_error_code VARCHAR(120),
    last_error_message VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    terminal_intent VARCHAR(30),
    compensation_reason VARCHAR(500),
    compensation_deadline_at TIMESTAMP WITH TIME ZONE,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_ord_saga PRIMARY KEY (saga_id),
    CONSTRAINT uq_ord_saga_order UNIQUE (tenant_id, order_id)
);
CREATE INDEX IF NOT EXISTS ix_ord_saga_claim ON ord_saga (status, next_attempt_at, created_at);
CREATE INDEX IF NOT EXISTS ix_ord_saga_stale ON ord_saga (status, claimed_at);

CREATE TABLE IF NOT EXISTS ord_saga_step (
    step_id VARCHAR(36) NOT NULL,
    saga_id VARCHAR(36) NOT NULL,
    step_key VARCHAR(160) NOT NULL,
    sequence_no INTEGER NOT NULL,
    step_type VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL,
    order_line_id VARCHAR(36),
    external_reference_id VARCHAR(100),
    idempotency_key VARCHAR(180) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    claimed_at TIMESTAMP WITH TIME ZONE,
    last_error_code VARCHAR(120),
    last_error_message VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_ord_saga_step PRIMARY KEY (step_id),
    CONSTRAINT uq_ord_saga_step_key UNIQUE (saga_id, step_key),
    CONSTRAINT uq_ord_saga_step_sequence UNIQUE (saga_id, sequence_no)
);
CREATE INDEX IF NOT EXISTS ix_ord_saga_step_order ON ord_saga_step (saga_id, sequence_no);
CREATE INDEX IF NOT EXISTS ix_ord_saga_step_status ON ord_saga_step (status, next_attempt_at);
