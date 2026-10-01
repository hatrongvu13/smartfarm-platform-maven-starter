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

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "last_accepted_time_step")
    private Long lastAcceptedTimeStep;

    protected UserAuthenticatorEntity() {
    }

    public UserAuthenticatorEntity(String id, String userId, AuthenticatorType type,
            String displayName, String secretCiphertext) {
        this.id = requireText(id, "id");
        this.userId = requireText(userId, "userId");
        this.type = java.util.Objects.requireNonNull(type);
        this.displayName = requireText(displayName, "displayName");
        this.secretCiphertext = secretCiphertext;
        this.status = AuthenticatorStatus.PENDING;
    }

    public void activate(Instant occurredAt) {
        if (status != AuthenticatorStatus.PENDING) {
            throw new IllegalStateException("Only a pending authenticator can be activated");
        }
        status = AuthenticatorStatus.ACTIVE;
        verifiedAt = java.util.Objects.requireNonNull(occurredAt);
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

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public AuthenticatorType getType() { return type; }
    public AuthenticatorStatus getStatus() { return status; }
    public String getDisplayName() { return displayName; }
    public String getSecretCiphertext() { return secretCiphertext; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public Long getLastAcceptedTimeStep() { return lastAcceptedTimeStep; }
}
