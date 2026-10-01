package com.htv.smartfarm.identity.mfa.domain;

import java.time.Instant;
import java.util.Objects;

import com.htv.smartfarm.identity.shared.persistence.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "sf_mfa_challenge", indexes = {
        @Index(name = "ix_mfa_challenge_user_status", columnList = "user_id,status"),
        @Index(name = "ix_mfa_challenge_expiry", columnList = "expires_at")
})
public class MfaChallengeEntity extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, updatable = false, length = 36)
    private String userId;

    @Column(name = "tenant_id", nullable = false, updatable = false, length = 36)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, updatable = false, length = 30)
    private MfaChallengePurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MfaChallengeStatus status;

    @Column(name = "challenge_hash", nullable = false, unique = true, length = 64)
    private String challengeHash;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected MfaChallengeEntity() {
    }

    public MfaChallengeEntity(String id, String userId, String tenantId,
            MfaChallengePurpose purpose, String challengeHash, Instant expiresAt) {
        this.id = requireText(id, "id");
        this.userId = requireText(userId, "userId");
        this.tenantId = requireText(tenantId, "tenantId");
        this.purpose = Objects.requireNonNull(purpose);
        this.challengeHash = requireText(challengeHash, "challengeHash");
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.status = MfaChallengeStatus.PENDING;
    }

    public void recordFailure(int maximumAttempts, Instant now) {
        requirePending(now);
        attemptCount++;
        if (attemptCount >= maximumAttempts) status = MfaChallengeStatus.LOCKED;
    }

    public void verify(Instant now) {
        requirePending(now);
        status = MfaChallengeStatus.VERIFIED;
        verifiedAt = now;
    }

    public void consume(Instant now) {
        if (status != MfaChallengeStatus.VERIFIED) {
            throw new IllegalStateException("Challenge has not been verified");
        }
        status = MfaChallengeStatus.CONSUMED;
        consumedAt = Objects.requireNonNull(now);
    }

    public void expire() {
        if (status == MfaChallengeStatus.PENDING) status = MfaChallengeStatus.EXPIRED;
    }

    public boolean expiredAt(Instant now) { return !expiresAt.isAfter(now); }

    private void requirePending(Instant now) {
        if (expiredAt(now)) {
            status = MfaChallengeStatus.EXPIRED;
            throw new IllegalStateException("Challenge has expired");
        }
        if (status != MfaChallengeStatus.PENDING) {
            throw new IllegalStateException("Challenge is not pending");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getTenantId() { return tenantId; }
    public MfaChallengePurpose getPurpose() { return purpose; }
    public MfaChallengeStatus getStatus() { return status; }
    public String getChallengeHash() { return challengeHash; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public Instant getConsumedAt() { return consumedAt; }
}
