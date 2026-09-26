package com.htv.smartfarm.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "smartfarm.identity")
public record IdentitySettings(String issuer, String gatewayAudience, String keyId, String privateKeyPath,
                               String publicKeyPath,
                               Duration accessTtl, Duration refreshTtl, int maxFailures, Duration lockDuration,
                               boolean publicRegistration,
                               String registrationTenant, String bootstrapTenant, String bootstrapEmail,
                               String bootstrapPassword) {
    public IdentitySettings {
        if (issuer == null || issuer.isBlank() || gatewayAudience == null || gatewayAudience.isBlank() || keyId == null || keyId.isBlank()
                || privateKeyPath == null || privateKeyPath.isBlank() || publicKeyPath == null || publicKeyPath.isBlank())
            throw new IllegalArgumentException("issuer, audience, key id and both RSA key paths required");
        if (accessTtl == null || accessTtl.isNegative() || accessTtl.isZero() || accessTtl.compareTo(Duration.ofMinutes(15)) > 0
                || refreshTtl == null || refreshTtl.isNegative() || refreshTtl.isZero() || refreshTtl.compareTo(Duration.ofDays(30)) > 0
                || maxFailures < 1 || lockDuration == null || lockDuration.isNegative() || lockDuration.isZero())
            throw new IllegalArgumentException("invalid TTL or lockout policy");
        if (publicRegistration && (registrationTenant == null || registrationTenant.isBlank()))
            throw new IllegalArgumentException("public registration requires fixed registration tenant");
    }
}
