package com.htv.smartfarm.order.readmodel.recovery;

import java.time.Instant;
import jakarta.persistence.*;

@Entity
@Table(name = "ord_projection_gap",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_ord_projection_gap_aggregate",
                columnNames = {"tenant_id", "aggregate_id"}),
        indexes = {
                @Index(name = "ix_ord_projection_gap_claim", columnList = "status,next_recovery_at,first_detected_at"),
                @Index(name = "ix_ord_projection_gap_stale", columnList = "status,claimed_at")
        })
public class OrderProjectionGapEntity {
    @Id @Column(name = "gap_id", length = 36) private String id;
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 100) private String tenantId;
    @Column(name = "aggregate_id", nullable = false, updatable = false, length = 36) private String aggregateId;
    @Column(name = "current_version", nullable = false) private long currentVersion;
    @Column(name = "missing_from_version", nullable = false) private long missingFromVersion;
    @Column(name = "missing_to_version", nullable = false) private long missingToVersion;
    @Column(name = "highest_buffered_version", nullable = false) private long highestBufferedVersion;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 30)
    private OrderProjectionGapStatus status;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "first_detected_at", nullable = false, updatable = false) private Instant firstDetectedAt;
    @Column(name = "last_checked_at") private Instant lastCheckedAt;
    @Column(name = "next_recovery_at", nullable = false) private Instant nextRecoveryAt;
    @Column(name = "claimed_at") private Instant claimedAt;
    @Column(name = "claim_owner", length = 120) private String claimOwner;
    @Column(name = "last_error_code", length = 120) private String lastErrorCode;
    @Column(name = "last_error_message", length = 500) private String lastErrorMessage;
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Version @Column(name = "row_version", nullable = false) private long rowVersion;

    protected OrderProjectionGapEntity() { }

    public OrderProjectionGapEntity(String id, String tenantId, String aggregateId,
            long currentVersion, long missingFromVersion, long missingToVersion,
            long highestBufferedVersion, Instant now) {
        this.id = required(id, "id"); this.tenantId = required(tenantId, "tenantId");
        this.aggregateId = required(aggregateId, "aggregateId");
        if (currentVersion < 0 || missingFromVersion < 1 || missingToVersion < missingFromVersion
                || highestBufferedVersion <= missingToVersion) {
            throw new IllegalArgumentException("invalid projection gap version range");
        }
        this.currentVersion = currentVersion; this.missingFromVersion = missingFromVersion;
        this.missingToVersion = missingToVersion; this.highestBufferedVersion = highestBufferedVersion;
        this.status = OrderProjectionGapStatus.OPEN;
        this.firstDetectedAt = java.util.Objects.requireNonNull(now);
        this.nextRecoveryAt = now;
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getAggregateId() { return aggregateId; }
    public long getCurrentVersion() { return currentVersion; }
    public long getMissingFromVersion() { return missingFromVersion; }
    public long getMissingToVersion() { return missingToVersion; }
    public long getHighestBufferedVersion() { return highestBufferedVersion; }
    public OrderProjectionGapStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getFirstDetectedAt() { return firstDetectedAt; }
    public Instant getLastCheckedAt() { return lastCheckedAt; }
    public Instant getNextRecoveryAt() { return nextRecoveryAt; }
    public Instant getClaimedAt() { return claimedAt; }
    public String getClaimOwner() { return claimOwner; }
    public String getLastErrorCode() { return lastErrorCode; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public Instant getResolvedAt() { return resolvedAt; }

    public void extend(long projectionVersion, long incomingVersion, Instant now) {
        if (status == OrderProjectionGapStatus.RESOLVED) {
            status = OrderProjectionGapStatus.OPEN;
            resolvedAt = null;
        }
        currentVersion = Math.max(currentVersion, projectionVersion);
        missingFromVersion = Math.max(currentVersion + 1, Math.min(missingFromVersion, incomingVersion - 1));
        missingToVersion = Math.max(missingToVersion, incomingVersion - 1);
        highestBufferedVersion = Math.max(highestBufferedVersion, incomingVersion);
        nextRecoveryAt = java.util.Objects.requireNonNull(now);
    }

    public void claim(String owner, Instant now) {
        if (!status.claimable()) throw new IllegalStateException("gap is not claimable");
        status = OrderProjectionGapStatus.RECOVERING;
        claimOwner = required(owner, "owner");
        claimedAt = java.util.Objects.requireNonNull(now);
        lastCheckedAt = now;
    }

    public void retry(Instant next, String code, String message, Instant now) {
        attemptCount++;
        status = OrderProjectionGapStatus.RETRY_WAIT;
        nextRecoveryAt = java.util.Objects.requireNonNull(next);
        claimedAt = null; claimOwner = null; lastCheckedAt = now;
        lastErrorCode = limited(code, 120); lastErrorMessage = limited(message, 500);
    }

    public void progress(long projectionVersion, long nextMissingVersion, Instant now) {
        currentVersion = projectionVersion;
        missingFromVersion = nextMissingVersion;
        lastCheckedAt = now;
        claimedAt = null; claimOwner = null;
        status = OrderProjectionGapStatus.OPEN;
        nextRecoveryAt = now;
    }

    public void resolve(long projectionVersion, Instant now) {
        currentVersion = projectionVersion;
        status = OrderProjectionGapStatus.RESOLVED;
        claimedAt = null; claimOwner = null; resolvedAt = now; lastCheckedAt = now;
        lastErrorCode = null; lastErrorMessage = null;
    }

    public void manualReview(String code, String message, Instant now) {
        status = OrderProjectionGapStatus.MANUAL_REVIEW;
        claimedAt = null; claimOwner = null; lastCheckedAt = now;
        lastErrorCode = limited(code, 120); lastErrorMessage = limited(message, 500);
    }

    public void retryManually(Instant now) {
        if (status != OrderProjectionGapStatus.MANUAL_REVIEW) {
            throw new IllegalStateException("only manual-review gaps can be retried manually");
        }
        status = OrderProjectionGapStatus.OPEN;
        attemptCount = 0;
        claimedAt = null;
        claimOwner = null;
        nextRecoveryAt = java.util.Objects.requireNonNull(now);
        lastCheckedAt = now;
        resolvedAt = null;
        lastErrorCode = null;
        lastErrorMessage = null;
    }

    public void recoverStale(Instant now) {
        if (status != OrderProjectionGapStatus.RECOVERING) return;
        status = OrderProjectionGapStatus.RETRY_WAIT;
        claimedAt = null; claimOwner = null; nextRecoveryAt = now; lastCheckedAt = now;
        lastErrorCode = "STALE_CLAIM";
        lastErrorMessage = "Projection gap claim recovered after timeout";
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
    private static String limited(String value, int maximum) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        return normalized.length() <= maximum ? normalized : normalized.substring(0, maximum);
    }
}
