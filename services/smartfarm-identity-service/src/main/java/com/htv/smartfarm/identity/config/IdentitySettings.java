package com.htv.smartfarm.identity.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartfarm.identity")
public record IdentitySettings(
        String issuer,
        String gatewayAudience,
        String identityAudience,
        String keyId,
        String privateKeyPath,
        String publicKeyPath,
        Duration accessTtl,
        Duration refreshTtl,
        int maxFailures,
        Duration lockDuration,
        boolean publicRegistration,
        String registrationTenant,
        String bootstrapTenant,
        String bootstrapEmail,
        String bootstrapPassword
) {
    public IdentitySettings {
        requireText(issuer, "issuer");
        requireText(gatewayAudience, "gatewayAudience");
        requireText(identityAudience, "identityAudience");
        requireText(keyId, "keyId");
        requireText(privateKeyPath, "privateKeyPath");
        requireText(publicKeyPath, "publicKeyPath");
        requireDuration(accessTtl, Duration.ofMinutes(15), "accessTtl");
        requireDuration(refreshTtl, Duration.ofDays(30), "refreshTtl");
        requireDuration(lockDuration, Duration.ofDays(1), "lockDuration");
        if (maxFailures < 1) {
            throw new IllegalArgumentException("maxFailures must be positive");
        }
        if (publicRegistration) {
            requireText(registrationTenant, "registrationTenant");
        }
    }

    private static void requireDuration(
            Duration value,
            Duration maximum,
            String field
    ) {
        if (value == null || value.isZero() || value.isNegative()
                || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(field + " is invalid");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
