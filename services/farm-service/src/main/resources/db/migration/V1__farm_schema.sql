-- FarmVerse farm-service — schema gốc (PostgreSQL), database riêng farmverse_farm.
-- Dữ liệu tách theo tenant_id (Hibernate @TenantId tự lọc).

CREATE TABLE farm (
    id          varchar(36)  PRIMARY KEY,
    tenant_id   varchar(36)  NOT NULL,
    code        varchar(50)  NOT NULL,
    name        varchar(200) NOT NULL,
    address     varchar(500),
    status      varchar(20)  NOT NULL,
    created_at  timestamptz  NOT NULL,
    updated_at  timestamptz  NOT NULL,
    version     bigint       NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, code)
);

-- Chuồng là value object trong aggregate farm (@ElementCollection).
CREATE TABLE barn (
    farm_id   varchar(36)  NOT NULL REFERENCES farm(id) ON DELETE CASCADE,
    position  integer      NOT NULL,
    code      varchar(40)  NOT NULL,
    name      varchar(120) NOT NULL,
    capacity  integer      NOT NULL CHECK (capacity > 0),
    PRIMARY KEY (farm_id, position),
    UNIQUE (farm_id, code)
);
