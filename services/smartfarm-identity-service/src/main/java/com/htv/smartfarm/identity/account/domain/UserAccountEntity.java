package com.htv.smartfarm.identity.account.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

import com.htv.smartfarm.identity.shared.persistence.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "sf_user_account",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_user_account_normalized_email",
                        columnNames = "normalized_email"
                )
        }
)
public class UserAccountEntity extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "normalized_email", nullable = false, length = 254)
    private String normalizedEmail;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    protected UserAccountEntity() {
    }

    public UserAccountEntity(
            String id,
            String email,
            String passwordHash
    ) {
        this.id = requireText(id, "id");
        changeEmail(email);
        this.passwordHash = requireText(passwordHash, "passwordHash");
        this.enabled = true;
        this.failedAttempts = 0;
    }

    public void changeEmail(String email) {
        String validatedEmail = requireText(email, "email").trim();
        this.email = validatedEmail;
        this.normalizedEmail = validatedEmail.toLowerCase(Locale.ROOT);
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = requireText(passwordHash, "passwordHash");
        resetLoginFailure();
    }

    public void recordLoginFailure(int lockThreshold, Instant lockUntil) {
        this.failedAttempts++;

        if (this.failedAttempts >= lockThreshold) {
            this.lockedUntil = Objects.requireNonNull(
                    lockUntil,
                    "lockUntil must not be null"
            );
        }
    }

    public void resetLoginFailure() {
        this.failedAttempts = 0;
        this.lockedUntil = null;
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    public boolean isLoginAllowed(Instant now) {
        return enabled
                && (lockedUntil == null || !lockedUntil.isAfter(now));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getNormalizedEmail() {
        return normalizedEmail;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }
}