package com.htv.farmverse.identity.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import com.htv.farmverse.identity.mfa.domain.AuthenticatorType;
import com.htv.farmverse.identity.mfa.domain.RecoveryCodeEntity;
import com.htv.farmverse.identity.mfa.domain.UserAuthenticatorEntity;

import org.junit.jupiter.api.Test;

class MfaReplayProtectionTest {

    private static final Instant NOW = Instant.parse("2026-10-16T00:00:00Z");

    @Test
    void acceptedTotpTimeStepCannotBeReusedOrMovedBackwards() {
        var authenticator = authenticator();
        authenticator.activate(NOW);

        authenticator.acceptTotpStep(100, NOW);

        assertThatThrownBy(() -> authenticator.acceptTotpStep(100, NOW.plusSeconds(30)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TOTP code was already used");
        assertThatThrownBy(() -> authenticator.acceptTotpStep(99, NOW.plusSeconds(30)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TOTP code was already used");
    }

    @Test
    void recoveryCodeCanOnlyBeConsumedOnce() {
        var recoveryCode = new RecoveryCodeEntity(
                "recovery-1",
                "auth-1",
                "hashed-recovery-code"
        );

        recoveryCode.consume(NOW);

        assertThat(recoveryCode.isUsed()).isTrue();
        assertThatThrownBy(() -> recoveryCode.consume(NOW.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Recovery code was already used");
    }

    private UserAuthenticatorEntity authenticator() {
        return new UserAuthenticatorEntity(
                "auth-1",
                "user-1",
                AuthenticatorType.TOTP,
                "Phone",
                "encrypted-secret",
                NOW.plusSeconds(300)
        );
    }
}
