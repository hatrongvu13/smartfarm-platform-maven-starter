package com.htv.smartfarm.order.readmodel.recovery;

import jakarta.persistence.*;

@Entity
@Table(name = "ord_order_event_archive",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_ord_event_archive_version",
                columnNames = {"tenant_id", "aggregate_id", "aggregate_version"}),
        indexes = @Index(name = "ix_ord_event_archive_aggregate",
                columnList = "tenant_id,aggregate_id,aggregate_version"))
public class OrderEventArchiveEntity {
    @Id @Column(name = "event_id", length = 100) private String eventId;
    @Column(name = "tenant_id", nullable = false, length = 100) private String tenantId;
    @Column(name = "aggregate_id", nullable = false, length = 36) private String aggregateId;
    @Column(name = "aggregate_version", nullable = false) private long aggregateVersion;
    @Column(name = "event_type", nullable = false, length = 80) private String eventType;
    @Column(name = "correlation_id", length = 128) private String correlationId;
    @Column(name = "created_at", nullable = false) private long createdAt;
    @Column(name = "farm_id", nullable = false, length = 100) private String farmId;
    @Column(name = "batch_id", length = 100) private String batchId;
    @Column(name = "order_status", nullable = false, length = 40) private String orderStatus;
    @Column(name = "currency_code", nullable = false, length = 3) private String currencyCode;
    @Column(name = "total_minor", nullable = false) private long totalMinor;
    @Column(name = "failure_reason", length = 500) private String failureReason;
    protected OrderEventArchiveEntity() { }
    public OrderEventArchiveEntity(String eventId, String tenantId, String aggregateId,
            long aggregateVersion, String eventType, String correlationId, long createdAt,
            String farmId, String batchId, String orderStatus, String currencyCode,
            long totalMinor, String failureReason) {
        this.eventId = required(eventId, "eventId");
        this.tenantId = required(tenantId, "tenantId");
        this.aggregateId = required(aggregateId, "aggregateId");
        if (aggregateVersion < 1) throw new IllegalArgumentException("aggregateVersion must be positive");
        this.aggregateVersion = aggregateVersion;
        this.eventType = required(eventType, "eventType");
        this.correlationId = nullable(correlationId);
        this.createdAt = createdAt;
        this.farmId = required(farmId, "farmId");
        this.batchId = nullable(batchId);
        this.orderStatus = required(orderStatus, "orderStatus");
        this.currencyCode = required(currencyCode, "currencyCode");
        this.totalMinor = totalMinor;
        this.failureReason = limited(failureReason, 500);
    }
    public String getEventId() { return eventId; }
    public String getTenantId() { return tenantId; }
    public String getAggregateId() { return aggregateId; }
    public long getAggregateVersion() { return aggregateVersion; }
    public String getEventType() { return eventType; }
    public String getCorrelationId() { return correlationId; }
    public long getCreatedAt() { return createdAt; }
    public String getFarmId() { return farmId; }
    public String getBatchId() { return batchId; }
    public String getOrderStatus() { return orderStatus; }
    public String getCurrencyCode() { return currencyCode; }
    public long getTotalMinor() { return totalMinor; }
    public String getFailureReason() { return failureReason; }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
    private static String nullable(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String limited(String value, int maximum) {
        String normalized = nullable(value);
        if (normalized == null) return null;
        return normalized.length() <= maximum ? normalized : normalized.substring(0, maximum);
    }
}
