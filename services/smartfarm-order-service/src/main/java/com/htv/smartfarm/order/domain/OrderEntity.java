package com.htv.smartfarm.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

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

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Version
    @Column(name = "row_version", nullable = false)
    private long version;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    protected OrderEntity() {
    }

    public OrderEntity(String id, String tenantId, String farmId, String batchId, String itemId, String warehouseId,
                       String quantity, String quantityUnit, String currencyCode, long totalMinor, String status,
                       long createdAt, String idempotencyKey) {
        this(id, tenantId, farmId, batchId, itemId, warehouseId, quantity, quantityUnit,
                currencyCode, totalMinor, status, createdAt, idempotencyKey, null);
    }

    public OrderEntity(String id, String tenantId, String farmId, String batchId, String itemId, String warehouseId,
                       String quantity, String quantityUnit, String currencyCode, long totalMinor, String status,
                       long createdAt, String idempotencyKey, String actorId) {
        this.id = required(id, "id");
        this.tenantId = required(tenantId, "tenantId");
        this.farmId = required(farmId, "farmId");
        this.batchId = nullable(batchId);
        this.itemId = required(itemId, "itemId");
        this.warehouseId = required(warehouseId, "warehouseId");
        this.quantity = required(quantity, "quantity");
        this.quantityUnit = required(quantityUnit, "quantityUnit");
        this.currencyCode = required(currencyCode, "currencyCode");
        if (totalMinor < 0) throw new IllegalArgumentException("totalMinor must not be negative");
        this.totalMinor = totalMinor;
        this.status = OrderDomainStatus.fromStored(status).protoName();
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.createdBy = nullable(actorId);
        this.updatedBy = nullable(actorId);
        this.idempotencyKey = required(idempotencyKey, "idempotencyKey");
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
    public long getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public long getVersion() { return version; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public OrderDomainStatus domainStatus() { return OrderDomainStatus.fromStored(status); }

    public void submit(String actorId, long at) {
        transitionTo(OrderDomainStatus.CREATED, actorId, at);
    }

    public void markStockReserving(String actorId, long at) {
        transitionTo(OrderDomainStatus.STOCK_RESERVING, actorId, at);
    }

    public void markStockReserved(String actorId, long at) {
        transitionTo(OrderDomainStatus.STOCK_RESERVED, actorId, at);
    }

    public void markTaskScheduled(String actorId, long at) {
        transitionTo(OrderDomainStatus.TASK_SCHEDULED, actorId, at);
    }

    public void markFinancePosting(String actorId, long at) {
        transitionTo(OrderDomainStatus.FINANCE_POSTING, actorId, at);
    }

    public void markFinancePosted(String actorId, long at) {
        transitionTo(OrderDomainStatus.FINANCE_POSTED, actorId, at);
    }

    public void complete(String actorId, long at) {
        transitionTo(OrderDomainStatus.COMPLETED, actorId, at);
        failureReason = null;
    }

    public void fail(String reason, String actorId, long at) {
        transitionTo(OrderDomainStatus.FAILED, actorId, at);
        failureReason = limited(reason, 500);
    }

    public void requireManualReview(String reason, String actorId, long at) {
        transitionTo(OrderDomainStatus.MANUAL_REVIEW, actorId, at);
        failureReason = limited(reason, 500);
    }

    public void markCancelling(String reason, String actorId, long at) {
        transitionTo(OrderDomainStatus.CANCELLING, actorId, at);
        failureReason = limited(reason, 500);
    }

    public void markCompensating(String reason, String actorId, long at) {
        if (domainStatus() == OrderDomainStatus.COMPENSATING) return;
        transitionTo(OrderDomainStatus.COMPENSATING, actorId, at);
        failureReason = limited(reason, 500);
    }

    public void cancel(String reason, String actorId, long at) {
        if (domainStatus() == OrderDomainStatus.CANCELLED) return;
        transitionTo(OrderDomainStatus.CANCELLED, actorId, at);
        failureReason = limited(reason, 500);
    }

    public void replaceDraftHeader(String farmId, String batchId, String itemId, String warehouseId,
                                   String quantity, String quantityUnit, String currencyCode, long totalMinor,
                                   String actorId, long at) {
        if (!domainStatus().editable()) throw new IllegalStateException("only draft orders can be edited");
        this.farmId = required(farmId, "farmId");
        this.batchId = nullable(batchId);
        this.itemId = required(itemId, "itemId");
        this.warehouseId = required(warehouseId, "warehouseId");
        this.quantity = required(quantity, "quantity");
        this.quantityUnit = required(quantityUnit, "quantityUnit");
        this.currencyCode = required(currencyCode, "currencyCode");
        if (totalMinor < 0) throw new IllegalArgumentException("totalMinor must not be negative");
        this.totalMinor = totalMinor;
        touch(actorId, at);
    }

    public void completeAdministratively(String reason, String actorId, long at) {
        if (domainStatus() == OrderDomainStatus.COMPLETED) return;
        if (domainStatus() == OrderDomainStatus.CANCELLED) {
            throw new IllegalStateException("cancelled order cannot be force-completed");
        }
        status = OrderDomainStatus.COMPLETED.protoName();
        failureReason = limited(reason, 500);
        touch(actorId, at);
    }

    public void cancelAdministratively(String reason, String actorId, long at) {
        if (domainStatus() == OrderDomainStatus.CANCELLED) return;
        if (domainStatus() == OrderDomainStatus.COMPLETED) {
            throw new IllegalStateException("completed order cannot be force-cancelled");
        }
        status = OrderDomainStatus.CANCELLED.protoName();
        failureReason = limited(reason, 500);
        touch(actorId, at);
    }

    public void resolveManualReview(String reason, String actorId, long at) {
        if (domainStatus() != OrderDomainStatus.MANUAL_REVIEW) {
            throw new IllegalStateException("only manual-review orders can be resolved manually");
        }
        failureReason = limited(reason, 500);
        touch(actorId, at);
    }

    /** Compatibility bridge for the synchronous Saga until Phase 3 migrates all callers. */
    @Deprecated(forRemoval = false)
    public void setStatus(String status) {
        transitionTo(OrderDomainStatus.fromStored(status), null, System.currentTimeMillis());
    }

    public void setFailureReason(String failureReason) { this.failureReason = limited(failureReason, 500); }
    public void setReservationId(String reservationId) { this.reservationId = nullable(reservationId); }
    public void setExpenseTxnId(String expenseTxnId) { this.expenseTxnId = nullable(expenseTxnId); }

    private void transitionTo(OrderDomainStatus target, String actorId, long at) {
        OrderDomainStatus current = domainStatus();
        if (current == target) return;
        if (!current.allowedNext().contains(target)) {
            throw new IllegalStateException("invalid order transition: " + current + " -> " + target);
        }
        status = target.protoName();
        touch(actorId, at);
    }

    private void touch(String actorId, long at) {
        if (at < createdAt) throw new IllegalArgumentException("updatedAt must not precede createdAt");
        updatedAt = at;
        if (actorId != null && !actorId.isBlank()) updatedBy = actorId.trim();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static String nullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String limited(String value, int maximum) {
        String normalized = nullable(value);
        if (normalized == null) return null;
        return normalized.length() <= maximum ? normalized : normalized.substring(0, maximum);
    }
}
