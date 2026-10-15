package com.htv.smartfarm.identity.mfa.domain;

import java.time.Instant;

import com.htv.smartfarm.identity.shared.persistence.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

@Getter
@Entity
@Table(name = "sf_user_authenticator", indexes = {
        @Index(name = "ix_authenticator_user_status", columnList = "user_id,status")
})
public class UserAuthenticatorEntity extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, updatable = false, length = 36)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "authenticator_type", nullable = false, length = 20)
    private AuthenticatorType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AuthenticatorStatus status;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "secret_ciphertext", length = 2048)
    private String secretCiphertext;

    @Column(name = "enrollment_expires_at")
    private Instant enrollmentExpiresAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "last_accepted_time_step")
    private Long lastAcceptedTimeStep;

    protected UserAuthenticatorEntity() {
    }

    public UserAuthenticatorEntity(String id, String userId, AuthenticatorType type,
            String displayName, String secretCiphertext,
            Instant enrollmentExpiresAt) {
        this.id = requireText(id, "id");
        this.userId = requireText(userId, "userId");
        this.type = java.util.Objects.requireNonNull(type);
        this.displayName = requireText(displayName, "displayName");
        this.secretCiphertext = secretCiphertext;
        this.enrollmentExpiresAt = java.util.Objects.requireNonNull(
                enrollmentExpiresAt,
                "enrollmentExpiresAt"
        );
        this.status = AuthenticatorStatus.PENDING;
    }

    public void activate(Instant occurredAt) {
        if (status != AuthenticatorStatus.PENDING) {
            throw new IllegalStateException("Only a pending authenticator can be activated");
        }
        Instant activationTime = java.util.Objects.requireNonNull(occurredAt);
        if (enrollmentExpiredAt(activationTime)) {
            throw new IllegalStateException("TOTP enrollment has expired");
        }
        status = AuthenticatorStatus.ACTIVE;
        verifiedAt = activationTime;
        enrollmentExpiresAt = null;
    }

    public boolean enrollmentExpiredAt(Instant now) {
        return enrollmentExpiresAt != null && !enrollmentExpiresAt.isAfter(now);
    }

    public void acceptTotpStep(long timeStep, Instant occurredAt) {
        if (status != AuthenticatorStatus.ACTIVE) {
            throw new IllegalStateException("Authenticator is not active");
        }
        if (lastAcceptedTimeStep != null && timeStep <= lastAcceptedTimeStep) {
            throw new IllegalStateException("TOTP code was already used");
        }
        lastAcceptedTimeStep = timeStep;
        lastUsedAt = java.util.Objects.requireNonNull(occurredAt);
    }

    public void disable() { status = AuthenticatorStatus.DISABLED; }
    public void compromise() { status = AuthenticatorStatus.COMPROMISED; }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

}
