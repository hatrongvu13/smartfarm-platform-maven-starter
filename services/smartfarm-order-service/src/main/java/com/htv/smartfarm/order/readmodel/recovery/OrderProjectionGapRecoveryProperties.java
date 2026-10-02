package com.htv.smartfarm.order.readmodel.recovery;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartfarm.order.readmodel.gap-recovery")
public record OrderProjectionGapRecoveryProperties(
        boolean enabled,
        int batchSize,
        int maximumAttempts,
        Duration initialRetry,
        Duration maximumRetry,
        Duration claimTimeout,
        Duration manualReviewAfter,
        String instanceId
) {
    public OrderProjectionGapRecoveryProperties {
        batchSize = batchSize <= 0 ? 20 : Math.min(batchSize, 200);
        maximumAttempts = maximumAttempts <= 0 ? 12 : maximumAttempts;
        initialRetry = duration(initialRetry, Duration.ofSeconds(10));
        maximumRetry = duration(maximumRetry, Duration.ofMinutes(10));
        claimTimeout = duration(claimTimeout, Duration.ofMinutes(2));
        manualReviewAfter = duration(manualReviewAfter, Duration.ofHours(24));
        instanceId = instanceId == null || instanceId.isBlank()
                ? "order-gap-recovery"
                : instanceId.trim();
    }
    private static Duration duration(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
