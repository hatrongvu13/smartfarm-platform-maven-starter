package com.htv.smartfarm.order.saga.persistence;

import java.time.Instant;
import jakarta.persistence.*;

@Entity
@Table(name = "ord_saga",
        uniqueConstraints = @UniqueConstraint(name = "uq_ord_saga_order", columnNames = {"tenant_id", "order_id"}),
        indexes = {
                @Index(name = "ix_ord_saga_claim", columnList = "status,next_attempt_at,created_at"),
                @Index(name = "ix_ord_saga_stale", columnList = "status,claimed_at")
        })
public class OrderSagaEntity {
    @Id @Column(name = "saga_id", nullable = false, updatable = false, length = 36) private String id;
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 100) private String tenantId;
    @Column(name = "order_id", nullable = false, updatable = false, length = 36) private String orderId;
    @Column(name = "actor_id", length = 100) private String actorId;
    @Column(name = "correlation_id", length = 128) private String correlationId;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 30) private OrderSagaStatus status;
    @Column(name = "current_step_key", length = 160) private String currentStepKey;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "next_attempt_at", nullable = false) private Instant nextAttemptAt;
    @Column(name = "claimed_at") private Instant claimedAt;
    @Column(name = "last_error_code", length = 120) private String lastErrorCode;
    @Column(name = "last_error_message", length = 500) private String lastErrorMessage;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "terminal_intent", length = 30)
    private OrderSagaTerminalIntent terminalIntent;
    @Column(name = "compensation_reason", length = 500)
    private String compensationReason;
    @Column(name = "compensation_deadline_at")
    private Instant compensationDeadlineAt;
    @Version @Column(name = "row_version", nullable = false) private long version;
    protected OrderSagaEntity() { }
    public OrderSagaEntity(String id, String tenantId, String orderId, String actorId, String correlationId, Instant now) {
        this.id = text(id, "id"); this.tenantId = text(tenantId, "tenantId"); this.orderId = text(orderId, "orderId");
        this.actorId = nullable(actorId); this.correlationId = nullable(correlationId); this.status = OrderSagaStatus.PENDING;
        this.createdAt = required(now, "now"); this.updatedAt = now; this.nextAttemptAt = now;
    }
    public String getId() { return id; } public String getTenantId() { return tenantId; }
    public String getOrderId() { return orderId; } public String getActorId() { return actorId; }
    public String getCorrelationId() { return correlationId; } public OrderSagaStatus getStatus() { return status; }
    public String getCurrentStepKey() { return currentStepKey; } public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; } public Instant getClaimedAt() { return claimedAt; }
    public String getLastErrorCode() { return lastErrorCode; } public String getLastErrorMessage() { return lastErrorMessage; }
    public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; } public long getVersion() { return version; }
    public OrderSagaTerminalIntent getTerminalIntent() { return terminalIntent; }
    public String getCompensationReason() { return compensationReason; }
    public Instant getCompensationDeadlineAt() { return compensationDeadlineAt; }

    public void claim(Instant now) {
        if (!status.claimable()) throw new IllegalStateException("saga is not claimable");
        status = status == OrderSagaStatus.COMPENSATING
                ? OrderSagaStatus.COMPENSATING
                : OrderSagaStatus.RUNNING;
        claimedAt = required(now, "now");
        updatedAt = now;
        lastErrorCode = null;
        lastErrorMessage = null;
    }

    public void focus(String stepKey, Instant now) {
        currentStepKey = text(stepKey, "stepKey");
        updatedAt = required(now, "now");
    }

    public void resume(Instant now) {
        claimedAt = null;
        nextAttemptAt = required(now, "now");
        updatedAt = now;
        lastErrorCode = null;
        lastErrorMessage = null;
    }

    public void retry(Instant nextAttempt, String errorCode, String errorMessage, Instant now) {
        attemptCount++;
        nextAttemptAt = required(nextAttempt, "nextAttempt");
        claimedAt = null;
        lastErrorCode = limited(errorCode, 120);
        lastErrorMessage = limited(errorMessage, 500);
        updatedAt = required(now, "now");
    }

    public void beginCompensation(Instant now) {
        beginCompensation(
                OrderSagaTerminalIntent.FAILED,
                "Forward saga failed",
                required(now, "now").plusSeconds(21600),
                now
        );
    }

    public void beginCompensation(
            OrderSagaTerminalIntent terminalIntent,
            String reason,
            Instant deadline,
            Instant now
    ) {
        if (status.terminal()) throw new IllegalStateException("terminal saga cannot compensate");
        status = OrderSagaStatus.COMPENSATING;
        attemptCount = 0;
        this.terminalIntent = required(terminalIntent, "terminalIntent");
        compensationReason = limited(reason, 500);
        compensationDeadlineAt = required(deadline, "deadline");
        claimedAt = null;
        nextAttemptAt = required(now, "now");
        updatedAt = now;
    }

    public boolean compensationDeadlineExceeded(Instant now) {
        return compensationDeadlineAt != null && !compensationDeadlineAt.isAfter(now);
    }

    public void complete(Instant now) {
        status = OrderSagaStatus.COMPLETED;
        currentStepKey = null;
        claimedAt = null;
        completedAt = required(now, "now");
        updatedAt = now;
    }

    public void compensated(Instant now) {
        status = OrderSagaStatus.COMPENSATED;
        currentStepKey = null;
        claimedAt = null;
        completedAt = required(now, "now");
        updatedAt = now;
    }

    public void fail(String errorCode, String errorMessage, Instant now) {
        status = OrderSagaStatus.FAILED;
        claimedAt = null;
        completedAt = required(now, "now");
        updatedAt = now;
        lastErrorCode = limited(errorCode, 120);
        lastErrorMessage = limited(errorMessage, 500);
    }

    public void manualReview(String errorCode, String errorMessage, Instant now) {
        status = OrderSagaStatus.MANUAL_REVIEW;
        claimedAt = null;
        completedAt = required(now, "now");
        updatedAt = now;
        lastErrorCode = limited(errorCode, 120);
        lastErrorMessage = limited(errorMessage, 500);
    }

    public void resumeManually(Instant now) {
        if (status != OrderSagaStatus.MANUAL_REVIEW
                && status != OrderSagaStatus.FAILED) {
            throw new IllegalStateException("only failed or manual-review sagas can be resumed");
        }
        status = terminalIntent == null ? OrderSagaStatus.RUNNING : OrderSagaStatus.COMPENSATING;
        attemptCount = 0;
        completedAt = null;
        claimedAt = null;
        nextAttemptAt = required(now, "now");
        updatedAt = now;
        lastErrorCode = null;
        lastErrorMessage = null;
    }

    public void recover(Instant now) {
        if (claimedAt == null) return;
        claimedAt = null;
        nextAttemptAt = required(now, "now");
        updatedAt = now;
        lastErrorCode = "STALE_CLAIM";
        lastErrorMessage = "Saga claim recovered after timeout";
    }
    private static String text(String v, String f) { if (v == null || v.isBlank()) throw new IllegalArgumentException(f + " must not be blank"); return v.trim(); }
    private static String nullable(String v) { return v == null || v.isBlank() ? null : v.trim(); }
    private static String limited(String v, int maximum) {
        String normalized = nullable(v);
        if (normalized == null) return null;
        return normalized.length() <= maximum ? normalized : normalized.substring(0, maximum);
    }
    private static <T> T required(T v, String f) { if (v == null) throw new IllegalArgumentException(f + " must not be null"); return v; }
}
