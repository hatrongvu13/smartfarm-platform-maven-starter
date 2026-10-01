package com.htv.smartfarm.identity.mfa.domain;

import java.time.Instant;

import com.htv.smartfarm.identity.shared.persistence.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "sf_recovery_code", indexes = {
        @Index(name = "ix_recovery_authenticator", columnList = "authenticator_id")
})
public class RecoveryCodeEntity extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "authenticator_id", nullable = false, updatable = false, length = 36)
    private String authenticatorId;

    @Column(name = "code_hash", nullable = false, unique = true, length = 64)
    private String codeHash;

    @Column(name = "used_at")
    private Instant usedAt;

    protected RecoveryCodeEntity() {
    }

    public RecoveryCodeEntity(String id, String authenticatorId, String codeHash) {
        this.id = requireText(id, "id");
        this.authenticatorId = requireText(authenticatorId, "authenticatorId");
        this.codeHash = requireText(codeHash, "codeHash");
    }

    public void consume(Instant occurredAt) {
        if (usedAt != null) throw new IllegalStateException("Recovery code was already used");
        usedAt = java.util.Objects.requireNonNull(occurredAt);
    }

    public boolean isUsed() { return usedAt != null; }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    public String getId() { return id; }
    public String getAuthenticatorId() { return authenticatorId; }
    public String getCodeHash() { return codeHash; }
    public Instant getUsedAt() { return usedAt; }
}
