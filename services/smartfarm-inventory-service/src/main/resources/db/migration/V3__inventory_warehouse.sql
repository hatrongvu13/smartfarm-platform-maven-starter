CREATE TABLE IF NOT EXISTS inv_warehouse
(
    warehouse_id VARCHAR(36) PRIMARY KEY,
    tenant_id    VARCHAR(100) NOT NULL,
    farm_id      VARCHAR(100) NOT NULL,
    code         VARCHAR(60)  NOT NULL,
    name         VARCHAR(200) NOT NULL,
    description  VARCHAR(500),
    address      VARCHAR(500),
    status       VARCHAR(16)  NOT NULL,
    created_at   BIGINT       NOT NULL,
    updated_at   BIGINT       NOT NULL,
    created_by   VARCHAR(100) NOT NULL,
    updated_by   VARCHAR(100) NOT NULL,
    row_version  BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_inv_warehouse_code UNIQUE (tenant_id, farm_id, code)
);
CREATE INDEX IF NOT EXISTS ix_inv_warehouse_farm_status ON inv_warehouse (tenant_id, farm_id, status);
DO
$$
    BEGIN
        IF EXISTS (SELECT 1 FROM inv_lot GROUP BY warehouse_id HAVING count(DISTINCT tenant_id) > 1) THEN
            RAISE EXCEPTION 'legacy warehouse_id is shared by multiple tenants; assign globally unique warehouse ids before V3';
        END IF;
    END
$$;
INSERT INTO inv_warehouse(warehouse_id, tenant_id, farm_id, code, name, status, created_at, updated_at, created_by,
                          updated_by, row_version)
SELECT warehouse_id,
       min(tenant_id),
       min(farm_id),
       upper(warehouse_id),
       warehouse_id,
       'ACTIVE',
       0,
       0,
       'system:migration',
       'system:migration',
       0
FROM inv_lot
GROUP BY warehouse_id
ON CONFLICT DO NOTHING;
CREATE INDEX IF NOT EXISTS ix_inv_lot_warehouse_item ON inv_lot (tenant_id, warehouse_id, item_id);
