package com.htv.smartfarm.order.readmodel.recovery;

import java.time.Instant;
import jakarta.persistence.*;

@Entity
@Table(name = "ord_projection_recovery_audit", indexes = {
        @Index(name = "ix_ord_projection_recovery_gap", columnList = "gap_id,occurred_at"),
        @Index(name = "ix_ord_projection_recovery_aggregate", columnList = "tenant_id,aggregate_id,occurred_at")
})
public class OrderProjectionRecoveryAuditEntity {
    @Id @Column(name = "audit_id", length = 36) private String id;
    @Column(name = "tenant_id", nullable = false, length = 100) private String tenantId;
    @Column(name = "aggregate_id", nullable = false, length = 36) private String aggregateId;
    @Column(name = "gap_id", nullable = false, length = 36) private String gapId;
    @Column(name = "aggregate_version") private Long aggregateVersion;
    @Column(name = "event_id", length = 100) private String eventId;
    @Column(name = "actor_id", nullable = false, length = 120) private String actorId;
    @Enumerated(EnumType.STRING) @Column(name = "action", nullable = false, length = 40)
    private OrderProjectionRecoveryAction action;
    @Column(name = "previous_status", nullable = false, length = 30) private String previousStatus;
    @Column(name = "new_status", nullable = false, length = 30) private String newStatus;
    @Column(name = "reason", nullable = false, length = 500) private String reason;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    protected OrderProjectionRecoveryAuditEntity() { }
    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getAggregateId() { return aggregateId; }
    public String getGapId() { return gapId; }
    public Long getAggregateVersion() { return aggregateVersion; }
    public String getEventId() { return eventId; }
    public String getActorId() { return actorId; }
    public OrderProjectionRecoveryAction getAction() { return action; }
    public String getPreviousStatus() { return previousStatus; }
    public String getNewStatus() { return newStatus; }
    public String getReason() { return reason; }
    public Instant getOccurredAt() { return occurredAt; }
    public OrderProjectionRecoveryAuditEntity(String id, String tenantId, String aggregateId,
            String gapId, Long aggregateVersion, String eventId, String actorId,
            OrderProjectionRecoveryAction action, String previousStatus, String newStatus,
            String reason, Instant occurredAt) {
        this.id = required(id, "id"); this.tenantId = required(tenantId, "tenantId");
        this.aggregateId = required(aggregateId, "aggregateId"); this.gapId = required(gapId, "gapId");
        this.aggregateVersion = aggregateVersion; this.eventId = nullable(eventId);
        this.actorId = required(actorId, "actorId"); this.action = java.util.Objects.requireNonNull(action);
        this.previousStatus = required(previousStatus, "previousStatus");
        this.newStatus = required(newStatus, "newStatus"); this.reason = limited(reason, 500);
        this.occurredAt = java.util.Objects.requireNonNull(occurredAt);
    }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
    private static String nullable(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String limited(String value, int maximum) {
        String normalized = required(value, "reason");
        return normalized.length() <= maximum ? normalized : normalized.substring(0, maximum);
    }
}
