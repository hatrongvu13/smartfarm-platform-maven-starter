package com.htv.smartfarm.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * JPA mapping of {@code ord_order} — the saga aggregate. {@code status} is the saga
 * state ({@code smartfarm.order.v1.OrderStatus}), advanced as each step succeeds and set
 * to FAILED/CANCELLED when compensation runs. Idempotent on (tenant_id, idempotency_key)
 * so a retried PlaceOrder returns the existing order instead of starting a second saga.
 * The compensation pointers ({@code reservationId}, {@code expenseTxnId}) are recorded as
 * each forward step completes so the compensating actions know exactly what to undo.
 */
@Entity
@Table(name = "ord_order",
        uniqueConstraints = @UniqueConstraint(name = "uq_ord_idem", columnNames = {"tenant_id", "idempotency_key"}),
        indexes = @Index(name = "ix_ord_farm", columnList = "tenant_id,farm_id"))
public class OrderEntity {

    @Id
    @Column(name = "order_id", length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "farm_id", nullable = false, length = 100)
    private String farmId;

    @Column(name = "batch_id", length = 100)
    private String batchId;

    @Column(name = "item_id", nullable = false, length = 36)
    private String itemId;

    @Column(name = "warehouse_id", nullable = false, length = 100)
    private String warehouseId;

    @Column(name = "quantity", nullable = false, length = 40)
    private String quantity;

    @Column(name = "quantity_unit", nullable = false, length = 20)
    private String quantityUnit;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "total_minor", nullable = false)
    private long totalMinor;

    /** Saga state name from OrderStatus, e.g. ORDER_STATUS_STOCK_RESERVED. */
    @Column(name = "status", nullable = false, length = 40)
    private String status;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    // ---- compensation pointers, filled as forward steps succeed ----
    @Column(name = "reservation_id", length = 36)
    private String reservationId;

    @Column(name = "expense_txn_id", length = 36)
    private String expenseTxnId;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    protected OrderEntity() {
    }

    public OrderEntity(String id, String tenantId, String farmId, String batchId, String itemId, String warehouseId,
                       String quantity, String quantityUnit, String currencyCode, long totalMinor, String status,
                       long createdAt, String idempotencyKey) {
        this.id = id;
        this.tenantId = tenantId;
        this.farmId = farmId;
        this.batchId = batchId;
        this.itemId = itemId;
        this.warehouseId = warehouseId;
        this.quantity = quantity;
        this.quantityUnit = quantityUnit;
        this.currencyCode = currencyCode;
        this.totalMinor = totalMinor;
        this.status = status;
        this.createdAt = createdAt;
        this.idempotencyKey = idempotencyKey;
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getFarmId() { return farmId; }
    public String getBatchId() { return batchId; }
    public String getItemId() { return itemId; }
    public String getWarehouseId() { return warehouseId; }
    public String getQuantity() { return quantity; }
    public String getQuantityUnit() { return quantityUnit; }
    public String getCurrencyCode() { return currencyCode; }
    public long getTotalMinor() { return totalMinor; }
    public String getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public String getReservationId() { return reservationId; }
    public String getExpenseTxnId() { return expenseTxnId; }
    public long getCreatedAt() { return createdAt; }
    public String getIdempotencyKey() { return idempotencyKey; }

    public void setStatus(String status) { this.status = status; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public void setReservationId(String reservationId) { this.reservationId = reservationId; }
    public void setExpenseTxnId(String expenseTxnId) { this.expenseTxnId = expenseTxnId; }
}
