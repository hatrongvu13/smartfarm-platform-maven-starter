package com.htv.smartfarm.order.readmodel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * CQRS read-model projection of an order, updated from {@code OrderChanged} integration events
 * (consumed off MQTT) rather than written by the saga directly. Kept separate from the saga
 * aggregate ({@code ord_order}) so reads never contend with the write path and the projection can
 * evolve independently. {@code lastEventAt} guards against applying an older event out of order.
 */
@Entity
@Table(name = "ord_order_view",
        indexes = @Index(name = "ix_ordview_farm", columnList = "tenant_id,farm_id,status"))
public class OrderView {

    @Id
    @Column(name = "order_id", length = 36)
    private String orderId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "farm_id", nullable = false, length = 100)
    private String farmId;

    @Column(name = "status", nullable = false, length = 40)
    private String status;

    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Column(name = "total_minor", nullable = false)
    private long totalMinor;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "last_event_at", nullable = false)
    private long lastEventAt;

    protected OrderView() {
    }

    public OrderView(String orderId, String tenantId, String farmId, String status, String currencyCode,
                     long totalMinor, String failureReason, long lastEventAt) {
        this.orderId = orderId;
        this.tenantId = tenantId;
        this.farmId = farmId;
        this.status = status;
        this.currencyCode = currencyCode;
        this.totalMinor = totalMinor;
        this.failureReason = failureReason;
        this.lastEventAt = lastEventAt;
    }

    public String getOrderId() { return orderId; }
    public String getTenantId() { return tenantId; }
    public String getFarmId() { return farmId; }
    public String getStatus() { return status; }
    public String getCurrencyCode() { return currencyCode; }
    public long getTotalMinor() { return totalMinor; }
    public String getFailureReason() { return failureReason; }
    public long getLastEventAt() { return lastEventAt; }

    public void apply(String status, String currencyCode, long totalMinor, String failureReason, long at) {
        this.status = status;
        this.currencyCode = currencyCode;
        this.totalMinor = totalMinor;
        this.failureReason = failureReason;
        this.lastEventAt = at;
    }
}
