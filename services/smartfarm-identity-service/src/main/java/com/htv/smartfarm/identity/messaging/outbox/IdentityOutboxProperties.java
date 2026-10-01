package com.htv.smartfarm.identity.messaging.outbox;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartfarm.identity.outbox")
public record IdentityOutboxProperties(
        boolean enabled,
        int batchSize,
        Duration pollInterval,
        int maximumAttempts,
        Duration initialRetry,
        Duration maximumRetry,
        Duration publishingTimeout,
        Duration publishedRetention,
        Duration cleanupInterval
) {
    public IdentityOutboxProperties {
        batchSize = batchSize <= 0 ? 50 : Math.min(batchSize, 500);
        pollInterval = positive(pollInterval, Duration.ofSeconds(2));
        maximumAttempts = maximumAttempts <= 0 ? 12 : maximumAttempts;
        initialRetry = positive(initialRetry, Duration.ofSeconds(5));
        maximumRetry = positive(maximumRetry, Duration.ofMinutes(5));
        publishingTimeout = positive(publishingTimeout, Duration.ofMinutes(2));
        publishedRetention = positive(publishedRetention, Duration.ofDays(7));
        cleanupInterval = positive(cleanupInterval, Duration.ofHours(1));
    }

    private static Duration positive(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
