package com.htv.smartfarm.order.saga.persistence;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartfarm.order.saga")
public record OrderSagaProperties(
        boolean enabled,
        int batchSize,
        int maximumAttempts,
        Duration initialRetry,
        Duration maximumRetry,
        Duration claimTimeout,
        Duration compensationDeadline
) {
    public OrderSagaProperties {
        batchSize = batchSize <= 0 ? 20 : Math.min(batchSize, 200);
        maximumAttempts = maximumAttempts <= 0 ? 8 : maximumAttempts;
        initialRetry = duration(initialRetry, Duration.ofSeconds(5));
        maximumRetry = duration(maximumRetry, Duration.ofMinutes(5));
        claimTimeout = duration(claimTimeout, Duration.ofMinutes(2));
        compensationDeadline = duration(compensationDeadline, Duration.ofHours(6));
    }
    private static Duration duration(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
