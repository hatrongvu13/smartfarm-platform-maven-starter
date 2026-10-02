package com.htv.smartfarm.order.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartfarm.outbox")
public record OrderOutboxProperties(
        boolean enabled,
        int batchSize,
        int maximumAttempts,
        Duration initialRetry,
        Duration maximumRetry,
        Duration publishingTimeout,
        Duration publishedRetention
) {
    public OrderOutboxProperties {
        batchSize = batchSize <= 0 ? 50 : Math.min(batchSize, 500);
        maximumAttempts = maximumAttempts <= 0 ? 12 : maximumAttempts;
        initialRetry = duration(initialRetry, Duration.ofSeconds(5));
        maximumRetry = duration(maximumRetry, Duration.ofMinutes(5));
        publishingTimeout = duration(publishingTimeout, Duration.ofMinutes(2));
        publishedRetention = duration(publishedRetention, Duration.ofDays(7));
    }
    private static Duration duration(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
