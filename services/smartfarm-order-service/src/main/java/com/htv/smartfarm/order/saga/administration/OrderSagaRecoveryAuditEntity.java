package com.htv.smartfarm.order.saga.administration;

import java.time.Instant;
import jakarta.persistence.*;

@Entity
@Table(name = "ord_saga_recovery_audit", indexes = {
        @Index(name = "ix_ord_saga_recovery_saga", columnList = "saga_id,occurred_at"),
        @Index(name = "ix_ord_saga_recovery_tenant", columnList = "tenant_id,occurred_at")
})
public class OrderSagaRecoveryAuditEntity {
    @Id @Column(name = "audit_id", length = 36) private String id;
    @Column(name = "tenant_id", nullable = false, length = 100) private String tenantId;
    @Column(name = "saga_id", nullable = false, length = 36) private String sagaId;
    @Column(name = "step_key", length = 160) private String stepKey;
    @Column(name = "actor_id", nullable = false, length = 100) private String actorId;
    @Enumerated(EnumType.STRING) @Column(name = "action", nullable = false, length = 30)
    private OrderSagaRecoveryAction action;
    @Column(name = "previous_status", nullable = false, length = 30) private String previousStatus;
    @Column(name = "new_status", nullable = false, length = 30) private String newStatus;
    @Column(name = "reason", nullable = false, length = 500) private String reason;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    protected OrderSagaRecoveryAuditEntity() { }
    public OrderSagaRecoveryAuditEntity(String id, String tenantId, String sagaId, String stepKey,
            String actorId, OrderSagaRecoveryAction action, String previousStatus,
            String newStatus, String reason, Instant occurredAt) {
        this.id = required(id, "id"); this.tenantId = required(tenantId, "tenantId");
        this.sagaId = required(sagaId, "sagaId"); this.stepKey = nullable(stepKey);
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
