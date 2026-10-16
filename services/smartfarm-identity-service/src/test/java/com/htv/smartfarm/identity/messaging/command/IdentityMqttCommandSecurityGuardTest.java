package com.htv.smartfarm.identity.messaging.command;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Set;

import com.htv.smartfarm.security.mqtt.MqttSecurityProperties;

import org.junit.jupiter.api.Test;

class IdentityMqttCommandSecurityGuardTest {

    @Test
    void allowsDisabledCommandsWithoutMqttVerification() {
        assertThatCode(() -> new IdentityMqttCommandSecurityGuard(
                commands(false),
                security(false, "")
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsEnabledCommandsWhenVerificationIsDisabled() {
        assertThatThrownBy(() -> new IdentityMqttCommandSecurityGuard(
                commands(true),
                security(false, "configured-key")
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "Identity MQTT commands require smartfarm.security.mqtt.verify-enabled=true"
                );
    }

    @Test
    void rejectsEnabledCommandsWithoutHmacSecret() {
        assertThatThrownBy(() -> new IdentityMqttCommandSecurityGuard(
                commands(true),
                security(true, " ")
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "Identity MQTT commands require a non-blank smartfarm.security.mqtt.hmac-secret"
                );
    }

    @Test
    void allowsEnabledCommandsWithVerificationAndHmacSecret() {
        assertThatCode(() -> new IdentityMqttCommandSecurityGuard(
                commands(true),
                security(true, "configured-key")
        )).doesNotThrowAnyException();
    }

    private IdentityMqttCommandProperties commands(boolean enabled) {
        return new IdentityMqttCommandProperties(
                enabled,
                null,
                1,
                65536,
                Set.of("identity.role.assign"),
                Duration.ofDays(30),
                25,
                5,
                Duration.ofSeconds(5),
                Duration.ofMinutes(5),
                Duration.ofMinutes(2)
        );
    }

    private MqttSecurityProperties security(boolean verifyEnabled, String secret) {
        return new MqttSecurityProperties(
                false,
                verifyEnabled,
                secret,
                "test-key",
                300_000
        );
    }
}
