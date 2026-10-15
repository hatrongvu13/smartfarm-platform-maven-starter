package com.htv.smartfarm.identity.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import com.htv.smartfarm.identity.mfa.domain.AuthenticatorStatus;
import com.htv.smartfarm.identity.mfa.domain.AuthenticatorType;
import com.htv.smartfarm.identity.mfa.domain.UserAuthenticatorEntity;

import org.junit.jupiter.api.Test;

class UserAuthenticatorEnrollmentExpiryTest {

    @Test
    void expiredEnrollmentCannotBeActivated() {
        Instant expiresAt = Instant.parse("2026-10-16T00:00:00Z");
        var authenticator = new UserAuthenticatorEntity(
                "auth-1",
                "user-1",
                AuthenticatorType.TOTP,
                "Phone",
                "encrypted-secret",
                expiresAt
        );

        assertThatThrownBy(() -> authenticator.activate(expiresAt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TOTP enrollment has expired");
        assertThat(authenticator.getStatus())
                .isEqualTo(AuthenticatorStatus.PENDING);
    }

    @Test
    void activationClearsPersistedEnrollmentExpiry() {
        Instant expiresAt = Instant.parse("2026-10-16T00:00:00Z");
        var authenticator = new UserAuthenticatorEntity(
                "auth-1",
                "user-1",
                AuthenticatorType.TOTP,
                "Phone",
                "encrypted-secret",
                expiresAt
        );

        authenticator.activate(expiresAt.minusSeconds(1));

        assertThat(authenticator.getStatus())
                .isEqualTo(AuthenticatorStatus.ACTIVE);
        assertThat(authenticator.getEnrollmentExpiresAt()).isNull();
    }
}
