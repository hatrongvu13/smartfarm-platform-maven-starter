-- Order service baseline schema (V1). Matches the JPA entities: saga aggregate, lines,
-- transactional outbox, and CQRS read-model. Money is integer minor units.

CREATE TABLE IF NOT EXISTS ord_order (
    order_id         VARCHAR(36)  NOT NULL,
    tenant_id        VARCHAR(100) NOT NULL,
    farm_id          VARCHAR(100) NOT NULL,
    batch_id         VARCHAR(100),
    item_id          VARCHAR(36)  NOT NULL,
    warehouse_id     VARCHAR(100) NOT NULL,
    quantity         VARCHAR(40)  NOT NULL,
    quantity_unit    VARCHAR(20)  NOT NULL,
    currency_code    VARCHAR(3)   NOT NULL,
    total_minor      BIGINT       NOT NULL,
    status           VARCHAR(40)  NOT NULL,
    failure_reason   VARCHAR(500),
    reservation_id   VARCHAR(36),
    expense_txn_id   VARCHAR(36),
    created_at       BIGINT       NOT NULL,
    idempotency_key  VARCHAR(128) NOT NULL,
    CONSTRAINT pk_ord_order PRIMARY KEY (order_id),
    CONSTRAINT uq_ord_idem UNIQUE (tenant_id, idempotency_key)
);
CREATE INDEX IF NOT EXISTS ix_ord_farm ON ord_order (tenant_id, farm_id);

CREATE TABLE IF NOT EXISTS ord_order_line (
    line_id           VARCHAR(36)  NOT NULL,
    order_id          VARCHAR(36)  NOT NULL,
    line_no           INTEGER      NOT NULL,
    item_id           VARCHAR(36)  NOT NULL,
    warehouse_id      VARCHAR(100) NOT NULL,
    quantity          VARCHAR(40)  NOT NULL,
    quantity_unit     VARCHAR(20)  NOT NULL,
    unit_price_minor  BIGINT       NOT NULL,
    line_total_minor  BIGINT       NOT NULL,
    reservation_id    VARCHAR(36),
    CONSTRAINT pk_ord_order_line PRIMARY KEY (line_id)
);
CREATE INDEX IF NOT EXISTS ix_ordline_order ON ord_order_line (order_id, line_no);

CREATE TABLE IF NOT EXISTS ord_outbox (
    event_id        VARCHAR(36)  NOT NULL,
    tenant_id       VARCHAR(100) NOT NULL,
    aggregate_id    VARCHAR(36)  NOT NULL,
    event_type      VARCHAR(80)  NOT NULL,
    correlation_id  VARCHAR(128),
    created_at      BIGINT       NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    CONSTRAINT pk_ord_outbox PRIMARY KEY (event_id)
);
CREATE INDEX IF NOT EXISTS ix_ord_outbox_status ON ord_outbox (status, created_at);

CREATE TABLE IF NOT EXISTS ord_order_view (
    order_id        VARCHAR(36)  NOT NULL,
    tenant_id       VARCHAR(100) NOT NULL,
    farm_id         VARCHAR(100) NOT NULL,
    status          VARCHAR(40)  NOT NULL,
    currency_code   VARCHAR(3),
    total_minor     BIGINT       NOT NULL,
    failure_reason  VARCHAR(500),
    last_event_at   BIGINT       NOT NULL,
    CONSTRAINT pk_ord_order_view PRIMARY KEY (order_id)
);
CREATE INDEX IF NOT EXISTS ix_ordview_farm ON ord_order_view (tenant_id, farm_id, status);
