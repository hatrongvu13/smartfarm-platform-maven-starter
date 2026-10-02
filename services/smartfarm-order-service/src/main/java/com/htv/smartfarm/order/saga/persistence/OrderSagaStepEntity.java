package com.htv.smartfarm.order.saga.persistence;

import java.time.Instant;
import jakarta.persistence.*;

@Entity
@Table(name = "ord_saga_step",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_ord_saga_step_key", columnNames = {"saga_id", "step_key"}),
                @UniqueConstraint(name = "uq_ord_saga_step_sequence", columnNames = {"saga_id", "sequence_no"})
        },
        indexes = {
                @Index(name = "ix_ord_saga_step_order", columnList = "saga_id,sequence_no"),
                @Index(name = "ix_ord_saga_step_status", columnList = "status,next_attempt_at")
        })
public class OrderSagaStepEntity {
    @Id @Column(name = "step_id", nullable = false, updatable = false, length = 36) private String id;
    @Column(name = "saga_id", nullable = false, updatable = false, length = 36) private String sagaId;
    @Column(name = "step_key", nullable = false, updatable = false, length = 160) private String stepKey;
    @Column(name = "sequence_no", nullable = false, updatable = false) private int sequenceNo;
    @Enumerated(EnumType.STRING) @Column(name = "step_type", nullable = false, updatable = false, length = 40) private OrderSagaStepType stepType;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 30) private OrderSagaStepStatus status;
    @Column(name = "order_line_id", length = 36) private String orderLineId;
    @Column(name = "external_reference_id", length = 100) private String externalReferenceId;
    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 180) private String idempotencyKey;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "next_attempt_at", nullable = false) private Instant nextAttemptAt;
    @Column(name = "claimed_at") private Instant claimedAt;
    @Column(name = "last_error_code", length = 120) private String lastErrorCode;
    @Column(name = "last_error_message", length = 500) private String lastErrorMessage;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Version @Column(name = "row_version", nullable = false) private long version;
    protected OrderSagaStepEntity() { }
    public OrderSagaStepEntity(String id, String sagaId, String stepKey, int sequenceNo, OrderSagaStepType stepType,
                               String orderLineId, String idempotencyKey, Instant now) {
        this.id = text(id, "id"); this.sagaId = text(sagaId, "sagaId"); this.stepKey = text(stepKey, "stepKey");
        if (sequenceNo < 0) throw new IllegalArgumentException("sequenceNo must not be negative");
        this.sequenceNo = sequenceNo; this.stepType = required(stepType, "stepType"); this.orderLineId = nullable(orderLineId);
        this.idempotencyKey = text(idempotencyKey, "idempotencyKey");
        this.status = stepType.compensation() ? OrderSagaStepStatus.SKIPPED : OrderSagaStepStatus.PENDING;
        this.createdAt = required(now, "now"); this.updatedAt = now; this.nextAttemptAt = now;
    }
    public String getId() { return id; } public String getSagaId() { return sagaId; }
    public String getStepKey() { return stepKey; } public int getSequenceNo() { return sequenceNo; }
    public OrderSagaStepType getStepType() { return stepType; } public OrderSagaStepStatus getStatus() { return status; }
    public String getOrderLineId() { return orderLineId; } public String getExternalReferenceId() { return externalReferenceId; }
    public String getIdempotencyKey() { return idempotencyKey; } public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; } public Instant getClaimedAt() { return claimedAt; }
    public String getLastErrorCode() { return lastErrorCode; } public String getLastErrorMessage() { return lastErrorMessage; }
    public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; } public long getVersion() { return version; }

    public void keepSkipped(Instant now) {
        if (!stepType.compensation()) throw new IllegalStateException("step is not compensation");
        status = OrderSagaStepStatus.SKIPPED;
        claimedAt = null;
        completedAt = required(now, "now");
        updatedAt = now;
    }

    public void activateCompensation(Instant now) {
        if (!stepType.compensation()) throw new IllegalStateException("step is not compensation");
        status = OrderSagaStepStatus.PENDING;
        nextAttemptAt = required(now, "now");
        updatedAt = now;
    }

    public void claim(Instant now) {
        if (status != OrderSagaStepStatus.PENDING && status != OrderSagaStepStatus.FAILED) {
            throw new IllegalStateException("step is not claimable");
        }
        status = stepType.compensation()
                ? OrderSagaStepStatus.COMPENSATING
                : OrderSagaStepStatus.PROCESSING;
        claimedAt = required(now, "now");
        updatedAt = now;
    }

    public void succeed(String externalReferenceId, Instant now) {
        status = OrderSagaStepStatus.SUCCEEDED;
        this.externalReferenceId = nullable(externalReferenceId);
        claimedAt = null;
        completedAt = required(now, "now");
        updatedAt = now;
        lastErrorCode = null;
        lastErrorMessage = null;
    }

    public void compensated(Instant now) {
        if (!stepType.compensation()) throw new IllegalStateException("step is not compensation");
        status = OrderSagaStepStatus.COMPENSATED;
        claimedAt = null;
        completedAt = required(now, "now");
        updatedAt = now;
        lastErrorCode = null;
        lastErrorMessage = null;
    }

    public void fail(Instant nextAttempt, String errorCode, String errorMessage, Instant now) {
        attemptCount++;
        status = OrderSagaStepStatus.FAILED;
        this.nextAttemptAt = required(nextAttempt, "nextAttempt");
        claimedAt = null;
        updatedAt = required(now, "now");
        lastErrorCode = limited(errorCode, 120);
        lastErrorMessage = limited(errorMessage, 500);
    }

    public void manualReview(String errorCode, String errorMessage, Instant now) {
        status = OrderSagaStepStatus.MANUAL_REVIEW;
        claimedAt = null;
        completedAt = required(now, "now");
        updatedAt = now;
        lastErrorCode = limited(errorCode, 120);
        lastErrorMessage = limited(errorMessage, 500);
    }

    public void retryManually(Instant now) {
        if (status != OrderSagaStepStatus.FAILED
                && status != OrderSagaStepStatus.MANUAL_REVIEW) {
            throw new IllegalStateException("only failed or manual-review steps can be retried");
        }
        status = OrderSagaStepStatus.FAILED;
        attemptCount = 0;
        claimedAt = null;
        completedAt = null;
        nextAttemptAt = required(now, "now");
        updatedAt = now;
        lastErrorCode = null;
        lastErrorMessage = null;
    }

    public void recover(Instant now) {
        if (status != OrderSagaStepStatus.PROCESSING && status != OrderSagaStepStatus.COMPENSATING) return;
        status = OrderSagaStepStatus.FAILED;
        claimedAt = null;
        nextAttemptAt = required(now, "now");
        updatedAt = now;
        lastErrorCode = "STALE_CLAIM";
        lastErrorMessage = "Saga step claim recovered after timeout";
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
