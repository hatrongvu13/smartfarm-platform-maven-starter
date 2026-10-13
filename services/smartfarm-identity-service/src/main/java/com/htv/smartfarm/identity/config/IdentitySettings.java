package com.htv.smartfarm.identity.config;

import java.time.Duration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "smartfarm.identity")
public record IdentitySettings(

        @NotBlank(message = "issuer must not be blank")
        String issuer,

        @NotBlank(message = "gatewayAudience must not be blank")
        String gatewayAudience,

        @NotBlank(message = "identityAudience must not be blank")
        String identityAudience,

        @NotBlank(message = "keyId must not be blank")
        String keyId,

        @NotBlank(message = "privateKeyPath must not be blank")
        String privateKeyPath,

        @NotBlank(message = "publicKeyPath must not be blank")
        String publicKeyPath,

        @NotNull(message = "accessTtl must not be null")
        Duration accessTtl,

        @NotNull(message = "refreshTtl must not be null")
        Duration refreshTtl,

        @Positive(message = "maxFailures must be positive")
        int maxFailures,

        @NotNull(message = "lockDuration must not be null")
        Duration lockDuration,

        boolean publicRegistration,

        String registrationTenant,

        String bootstrapTenant,

        String bootstrapEmail,

        String bootstrapPassword
) {

    private static final Duration MAX_ACCESS_TTL =
            Duration.ofMinutes(15);

    private static final Duration MAX_REFRESH_TTL =
            Duration.ofDays(30);

    private static final Duration MAX_LOCK_DURATION =
            Duration.ofDays(1);

    @AssertTrue(message = "accessTtl must be between 1 nanosecond and 15 minutes")
    public boolean isAccessTtlValid() {
        return isValidDuration(accessTtl, MAX_ACCESS_TTL);
    }

    @AssertTrue(message = "refreshTtl must be between 1 nanosecond and 30 days")
    public boolean isRefreshTtlValid() {
        return isValidDuration(refreshTtl, MAX_REFRESH_TTL);
    }

    @AssertTrue(message = "lockDuration must be between 1 nanosecond and 1 day")
    public boolean isLockDurationValid() {
        return isValidDuration(lockDuration, MAX_LOCK_DURATION);
    }

    @AssertTrue(
            message = "registrationTenant must not be blank "
                    + "when publicRegistration is enabled"
    )
    public boolean isRegistrationTenantValid() {
        return !publicRegistration
                || hasText(registrationTenant);
    }

    private static boolean isValidDuration(
            Duration value,
            Duration maximum
    ) {
        return value != null
                && !value.isZero()
                && !value.isNegative()
                && value.compareTo(maximum) <= 0;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}