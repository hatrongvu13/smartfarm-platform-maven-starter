package com.htv.smartfarm.order.readmodel;

import jakarta.persistence.*;

@Entity
@Table(name = "ord_order_view", indexes = @Index(name = "ix_ordview_farm", columnList = "tenant_id,farm_id,status"))
public class OrderView {
    @Id @Column(name = "order_id", length = 36) private String orderId;
    @Column(name = "tenant_id", nullable = false, length = 100) private String tenantId;
    @Column(name = "farm_id", nullable = false, length = 100) private String farmId;
    @Column(name = "status", nullable = false, length = 40) private String status;
    @Column(name = "currency_code", length = 3) private String currencyCode;
    @Column(name = "total_minor", nullable = false) private long totalMinor;
    @Column(name = "failure_reason", length = 500) private String failureReason;
    @Column(name = "last_event_at", nullable = false) private long lastEventAt;
    @Column(name = "last_aggregate_version", nullable = false) private long lastAggregateVersion;
    @Column(name = "last_event_id", length = 100) private String lastEventId;
    @Version @Column(name = "row_version", nullable = false) private long rowVersion;
    protected OrderView() { }
    public OrderView(String orderId, String tenantId, String farmId, String status, String currencyCode,
            long totalMinor, String failureReason, long lastEventAt, long lastAggregateVersion, String lastEventId) {
        this.orderId = orderId; this.tenantId = tenantId; this.farmId = farmId; this.status = status;
        this.currencyCode = currencyCode; this.totalMinor = totalMinor; this.failureReason = failureReason;
        this.lastEventAt = lastEventAt; this.lastAggregateVersion = lastAggregateVersion; this.lastEventId = lastEventId;
    }
    public String getOrderId() { return orderId; } public String getTenantId() { return tenantId; }
    public String getFarmId() { return farmId; } public String getStatus() { return status; }
    public String getCurrencyCode() { return currencyCode; } public long getTotalMinor() { return totalMinor; }
    public String getFailureReason() { return failureReason; } public long getLastEventAt() { return lastEventAt; }
    public long getLastAggregateVersion() { return lastAggregateVersion; } public String getLastEventId() { return lastEventId; }
    public void apply(String farmId, String status, String currencyCode, long totalMinor,
            String failureReason, long at, long aggregateVersion, String eventId) {
        this.farmId = farmId; this.status = status; this.currencyCode = currencyCode;
        this.totalMinor = totalMinor; this.failureReason = failureReason; this.lastEventAt = at;
        this.lastAggregateVersion = aggregateVersion; this.lastEventId = eventId;
    }
}
