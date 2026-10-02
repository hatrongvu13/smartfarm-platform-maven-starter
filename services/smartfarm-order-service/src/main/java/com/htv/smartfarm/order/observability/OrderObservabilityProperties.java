package com.htv.smartfarm.order.observability;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartfarm.order.observability")
public record OrderObservabilityProperties(
        Duration sagaOldestActiveWarning,
        long outboxPendingWarning,
        Duration outboxOldestPendingWarning,
        long projectionGapWarning,
        Duration projectionOldestGapWarning
) {
    public OrderObservabilityProperties {
        sagaOldestActiveWarning = positive(sagaOldestActiveWarning, Duration.ofMinutes(15));
        outboxPendingWarning = outboxPendingWarning <= 0 ? 1000 : outboxPendingWarning;
        outboxOldestPendingWarning = positive(outboxOldestPendingWarning, Duration.ofMinutes(5));
        projectionGapWarning = projectionGapWarning <= 0 ? 100 : projectionGapWarning;
        projectionOldestGapWarning = positive(projectionOldestGapWarning, Duration.ofMinutes(15));
    }
    private static Duration positive(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
