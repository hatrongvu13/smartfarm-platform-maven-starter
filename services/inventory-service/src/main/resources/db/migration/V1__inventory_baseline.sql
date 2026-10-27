-- Inventory service baseline schema (V1). Matches the JPA entities exactly
-- (inv_item, inv_lot, inv_balance, inv_movement, inv_reservation, inv_inbox).
-- Quantities are NUMERIC(18,3); received_at is epoch millis (BIGINT).
-- IF NOT EXISTS makes this safe over a DB already populated by the earlier ddl-auto phase.

CREATE TABLE IF NOT EXISTS inv_item (
    id         VARCHAR(36)  NOT NULL,
    tenant_id  VARCHAR(100) NOT NULL,
    sku        VARCHAR(80)  NOT NULL,
    name       VARCHAR(200) NOT NULL,
    unit       VARCHAR(20)  NOT NULL,
    category   VARCHAR(60)  NOT NULL,
    CONSTRAINT pk_inv_item PRIMARY KEY (id),
    CONSTRAINT uq_inv_item_sku UNIQUE (tenant_id, sku)
);

CREATE TABLE IF NOT EXISTS inv_lot (
    id            VARCHAR(36)  NOT NULL,
    tenant_id     VARCHAR(100) NOT NULL,
    item_id       VARCHAR(36)  NOT NULL,
    farm_id       VARCHAR(100) NOT NULL,
    warehouse_id  VARCHAR(100) NOT NULL,
    CONSTRAINT pk_inv_lot PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS inv_balance (
    tenant_id  VARCHAR(100)  NOT NULL,
    lot_id     VARCHAR(36)   NOT NULL,
    on_hand    NUMERIC(18,3) NOT NULL,
    reserved   NUMERIC(18,3) NOT NULL,
    CONSTRAINT pk_inv_balance PRIMARY KEY (tenant_id, lot_id)
);

CREATE TABLE IF NOT EXISTS inv_movement (
    id               VARCHAR(36)   NOT NULL,
    tenant_id        VARCHAR(100)  NOT NULL,
    lot_id           VARCHAR(36)   NOT NULL,
    kind             VARCHAR(32)   NOT NULL,
    quantity         NUMERIC(18,3) NOT NULL,
    reference_id     VARCHAR(128)  NOT NULL,
    idempotency_key  VARCHAR(128)  NOT NULL,
    CONSTRAINT pk_inv_movement PRIMARY KEY (id),
    CONSTRAINT uq_inv_mov_idem UNIQUE (tenant_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS inv_reservation (
    reservation_id   VARCHAR(36)   NOT NULL,
    tenant_id        VARCHAR(100)  NOT NULL,
    order_id         VARCHAR(36)   NOT NULL,
    item_id          VARCHAR(36)   NOT NULL,
    lot_id           VARCHAR(36)   NOT NULL,
    warehouse_id     VARCHAR(100)  NOT NULL,
    quantity         NUMERIC(18,3) NOT NULL,
    status           VARCHAR(16)   NOT NULL,
    idempotency_key  VARCHAR(128)  NOT NULL,
    CONSTRAINT pk_inv_reservation PRIMARY KEY (reservation_id),
    CONSTRAINT uq_inv_resv_idem UNIQUE (tenant_id, idempotency_key)
);
CREATE INDEX IF NOT EXISTS ix_inv_resv_order ON inv_reservation (tenant_id, order_id);

CREATE TABLE IF NOT EXISTS inv_inbox (
    event_id     VARCHAR(100) NOT NULL,
    tenant_id    VARCHAR(100) NOT NULL,
    task_id      VARCHAR(100) NOT NULL,
    received_at  BIGINT       NOT NULL,
    CONSTRAINT pk_inv_inbox PRIMARY KEY (event_id)
);
