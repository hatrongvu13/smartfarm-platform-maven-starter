package com.htv.smartfarm.inventory.domain.warehouse;

import jakarta.persistence.*;

@Entity
@Table(name = "inv_warehouse", uniqueConstraints = @UniqueConstraint(name = "uq_inv_warehouse_code", columnNames = {"tenant_id", "farm_id", "code"}), indexes = @Index(name = "ix_inv_warehouse_farm_status", columnList = "tenant_id,farm_id,status"))
public class WarehouseEntity {
    @Id
    @Column(name = "warehouse_id", length = 36)
    private String id;
    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;
    @Column(name = "farm_id", nullable = false, length = 100)
    private String farmId;
    @Column(nullable = false, length = 60)
    private String code;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(length = 500)
    private String description;
    @Column(length = 500)
    private String address;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WarehouseStatus status;
    @Column(name = "created_at", nullable = false, updatable = false)
    private long createdAt;
    @Column(name = "updated_at", nullable = false)
    private long updatedAt;
    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;
    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;
    @Version
    @Column(name = "row_version", nullable = false)
    private long version;

    protected WarehouseEntity() {
    }

    public WarehouseEntity(String id, String tenant, String farm, String code, String name, String description, String address, String actor, long now) {
        this.id = req(id, "warehouseId");
        this.tenantId = req(tenant, "tenantId");
        this.farmId = req(farm, "farmId");
        this.code = req(code, "code").toUpperCase();
        this.name = req(name, "name");
        this.description = clean(description);
        this.address = clean(address);
        this.status = WarehouseStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = req(actor, "actorId");
        this.updatedBy = this.createdBy;
    }

    private static String req(String v, String n) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException(n + " is required");
        return v.trim();
    }

    private static String clean(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    public String getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getFarmId() {
        return farmId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getAddress() {
        return address;
    }

    public WarehouseStatus getStatus() {
        return status;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public void update(String name, String description, String address) {
        this.name = req(name, "name");
        this.description = clean(description);
        this.address = clean(address);
        this.updatedAt = System.currentTimeMillis();
    }

    public void setActive(boolean active) {
        this.status = active ? WarehouseStatus.ACTIVE : WarehouseStatus.INACTIVE;
        this.updatedAt = System.currentTimeMillis();
    }
}
