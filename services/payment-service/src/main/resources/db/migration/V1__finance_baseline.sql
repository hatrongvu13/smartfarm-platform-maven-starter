-- Finance baseline schema (V1). Matches the JPA entities exactly so ddl-auto=validate passes.
-- Money is integer minor units + ISO-4217 currency; no floating point.

CREATE TABLE IF NOT EXISTS fin_transaction (
    transaction_id   VARCHAR(36)  NOT NULL,
    tenant_id        VARCHAR(100) NOT NULL,
    farm_id          VARCHAR(100) NOT NULL,
    batch_id         VARCHAR(100),
    kind             VARCHAR(16)  NOT NULL,
    currency_code    VARCHAR(3)   NOT NULL,
    minor_units      BIGINT       NOT NULL,
    category         VARCHAR(80),
    reference_type   VARCHAR(40),
    reference_id     VARCHAR(128),
    description      VARCHAR(500),
    occurred_at      BIGINT       NOT NULL,
    idempotency_key  VARCHAR(128) NOT NULL,
    CONSTRAINT pk_fin_transaction PRIMARY KEY (transaction_id),
    CONSTRAINT uq_fin_txn_idem UNIQUE (tenant_id, idempotency_key)
);
CREATE INDEX IF NOT EXISTS ix_fin_txn_batch ON fin_transaction (tenant_id, batch_id);

CREATE TABLE IF NOT EXISTS fin_debt (
    debt_id            VARCHAR(36)  NOT NULL,
    tenant_id          VARCHAR(100) NOT NULL,
    farm_id            VARCHAR(100) NOT NULL,
    counterparty_id    VARCHAR(100),
    kind               VARCHAR(16)  NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    currency_code      VARCHAR(3)   NOT NULL,
    principal_minor    BIGINT       NOT NULL,
    outstanding_minor  BIGINT       NOT NULL,
    due_at             BIGINT,
    reference_id       VARCHAR(128),
    idempotency_key    VARCHAR(128) NOT NULL,
    CONSTRAINT pk_fin_debt PRIMARY KEY (debt_id),
    CONSTRAINT uq_fin_debt_idem UNIQUE (tenant_id, idempotency_key)
);
CREATE INDEX IF NOT EXISTS ix_fin_debt_ref ON fin_debt (tenant_id, reference_id);
