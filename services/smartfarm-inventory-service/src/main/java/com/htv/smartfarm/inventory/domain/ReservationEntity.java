package com.htv.smartfarm.inventory.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * JPA mapping of {@code inv_reservation}. A reservation holds stock for a saga's order
 * without consuming it; it is later COMMITTED (stock consumed) or RELEASED (compensation).
 * Idempotent on (tenant_id, idempotency_key) so a retried ReserveStock does not double-hold.
 */
@Entity
@Table(name = "inv_reservation",
        uniqueConstraints = @UniqueConstraint(name = "uq_inv_resv_idem", columnNames = {"tenant_id", "idempotency_key"}),
        indexes = @Index(name = "ix_inv_resv_order", columnList = "tenant_id,order_id"))
public class ReservationEntity {

    @Id
    @Column(name = "reservation_id", length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "order_id", nullable = false, length = 36)
    private String orderId;

    @Column(name = "item_id", nullable = false, length = 36)
    private String itemId;

    @Column(name = "lot_id", nullable = false, length = 36)
    private String lotId;

    @Column(name = "warehouse_id", nullable = false, length = 100)
    private String warehouseId;

    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity;

    /** ACTIVE, RELEASED, COMMITTED. */
    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    protected ReservationEntity() {
    }

    public ReservationEntity(String id, String tenantId, String orderId, String itemId, String lotId,
                             String warehouseId, BigDecimal quantity, String status, String idempotencyKey) {
        this.id = id;
        this.tenantId = tenantId;
        this.orderId = orderId;
        this.itemId = itemId;
        this.lotId = lotId;
        this.warehouseId = warehouseId;
        this.quantity = quantity;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getOrderId() { return orderId; }
    public String getItemId() { return itemId; }
    public String getLotId() { return lotId; }
    public String getWarehouseId() { return warehouseId; }
    public BigDecimal getQuantity() { return quantity; }
    public String getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }

    public void markReleased() { this.status = "RELEASED"; }
    public void markCommitted() { this.status = "COMMITTED"; }
}
