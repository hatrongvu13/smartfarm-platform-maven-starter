package com.htv.smartfarm.order.outbox;

import java.time.Instant;

import com.htv.smartfarm.order.domain.OrderEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "ord_outbox", indexes = {
        @Index(name = "ix_ord_outbox_delivery", columnList = "status,next_attempt_at,created_at"),
        @Index(name = "ix_ord_outbox_aggregate", columnList = "tenant_id,aggregate_id,aggregate_version")
})
public class OrderOutboxEntity {
    @Id
    @Column(name = "event_id", length = 36)
    private String eventId;
    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;
    @Column(name = "aggregate_id", nullable = false, length = 36)
    private String aggregateId;
    @Column(name = "aggregate_version", nullable = false)
    private long aggregateVersion;
    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;
    @Column(name = "correlation_id", length = 128)
    private String correlationId;
    @Column(name = "created_at", nullable = false)
    private long createdAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OrderOutboxStatus status;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Column(name = "claimed_at")
    private Instant claimedAt;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "last_error_code", length = 120)
    private String lastErrorCode;

    // Immutable order snapshot captured in the same transaction as the transition.
    @Column(name = "farm_id", nullable = false, length = 100)
    private String farmId;
    @Column(name = "batch_id", length = 100)
    private String batchId;
    @Column(name = "order_status", nullable = false, length = 40)
    private String orderStatus;
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;
    @Column(name = "total_minor", nullable = false)
    private long totalMinor;
    @Column(name = "failure_reason", length = 500)
    private String failureReason;
    @Column(name = "actor_id", length = 100)
    private String actorId;
    @Column(name = "saga_id", length = 36)
    private String sagaId;
    @Column(name = "step_key", length = 160)
    private String stepKey;
    @Column(name = "previous_status", length = 40)
    private String previousStatus;
    @Column(name = "new_status", length = 40)
    private String newStatus;
    @Column(name = "reason_code", length = 120)
    private String reasonCode;
    @Column(name = "event_reason", length = 500)
    private String eventReason;
    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected OrderOutboxEntity() {
    }

    public static OrderOutboxEntity snapshot(
            String eventId,
            OrderEntity order,
            String eventType,
            String correlationId,
            long createdAt
    ) {
        return new OrderOutboxEntity(
                eventId, order.getTenantId(), order.getId(), order.getVersion() + 1, eventType,
                correlationId, createdAt, order.getFarmId(), order.getBatchId(), order.getStatus(),
                order.getCurrencyCode(), order.getTotalMinor(), order.getFailureReason());
    }

    public OrderOutboxEntity(String eventId, String tenantId, String aggregateId,
                             long aggregateVersion, String eventType, String correlationId, long createdAt,
                             String farmId, String batchId, String orderStatus, String currencyCode,
                             long totalMinor, String failureReason) {
        this.eventId = required(eventId, "eventId");
        this.tenantId = required(tenantId, "tenantId");
        this.aggregateId = required(aggregateId, "aggregateId");
        this.aggregateVersion = aggregateVersion;
        this.eventType = required(eventType, "eventType");
        this.correlationId = nullable(correlationId);
        this.createdAt = createdAt;
        this.status = OrderOutboxStatus.NEW;
        this.nextAttemptAt = Instant.ofEpochMilli(createdAt);
        this.farmId = required(farmId, "farmId");
        this.batchId = nullable(batchId);
        this.orderStatus = required(orderStatus, "orderStatus");
        this.currencyCode = required(currencyCode, "currencyCode");
        this.totalMinor = totalMinor;
        this.failureReason = limited(failureReason, 500);
    }

    public static OrderOutboxEntity business(String eventId, OrderEntity order,
            OrderBusinessEventType type, String correlationId, long createdAt,
            OrderBusinessEventContext context) {
        OrderOutboxEntity value = snapshot(eventId, order, type.eventType(), correlationId, createdAt);
        value.actorId = nullable(context.actorId());
        value.sagaId = nullable(context.sagaId());
        value.stepKey = nullable(context.stepKey());
        value.previousStatus = nullable(context.previousStatus());
        value.newStatus = nullable(context.newStatus());
        value.reasonCode = limited(context.reasonCode(), 120);
        value.eventReason = limited(context.reason(), 500);
        return value;
    }

    public String getEventId() {
        return eventId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public long getAggregateVersion() {
        return aggregateVersion;
    }

    public String getEventType() {
        return eventType;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public OrderOutboxStatus getStatus() {
        return status;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public String getFarmId() {
        return farmId;
    }

    public String getBatchId() {
        return batchId;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public long getTotalMinor() {
        return totalMinor;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getActorId() { return actorId; }
    public String getSagaId() { return sagaId; }
    public String getStepKey() { return stepKey; }
    public String getPreviousStatus() { return previousStatus; }
    public String getNewStatus() { return newStatus; }
    public String getReasonCode() { return reasonCode; }
    public String getEventReason() { return eventReason; }

    public long getRowVersion() {
        return rowVersion;
    }

    public void claim(Instant now) {
        if (!status.claimable()) throw new IllegalStateException("outbox event is not claimable");
        status = OrderOutboxStatus.PUBLISHING;
        claimedAt = now;
    }

    public void published(Instant now) {
        status = OrderOutboxStatus.PUBLISHED;
        claimedAt = null;
        publishedAt = now;
        lastErrorCode = null;
    }

    public void failed(Instant nextAttempt, String errorCode, int maximumAttempts) {
        attemptCount++;
        status = attemptCount >= maximumAttempts ? OrderOutboxStatus.DEAD : OrderOutboxStatus.FAILED;
        nextAttemptAt = nextAttempt;
        claimedAt = null;
        lastErrorCode = limited(errorCode, 120);
    }

    public void retryManually(Instant now) {
        if (status != OrderOutboxStatus.FAILED && status != OrderOutboxStatus.DEAD) {
            throw new IllegalStateException("only failed or dead outbox events can be retried");
        }
        status = OrderOutboxStatus.FAILED;
        attemptCount = 0;
        claimedAt = null;
        publishedAt = null;
        nextAttemptAt = java.util.Objects.requireNonNull(now);
        lastErrorCode = null;
    }

    public void recover(Instant now) {
        if (status != OrderOutboxStatus.PUBLISHING) return;
        status = OrderOutboxStatus.FAILED;
        claimedAt = null;
        nextAttemptAt = now;
        lastErrorCode = "STALE_CLAIM";
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

    public String getLastErrorCode() {
        return this.lastErrorCode;
    }
}
