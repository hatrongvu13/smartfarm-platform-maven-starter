package com.htv.smartfarm.order.readmodel.recovery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("orderProjectionGapRecovery")
@ConditionalOnProperty(prefix = "smartfarm.order.readmodel.gap-recovery",
        name = "enabled", havingValue = "true")
public class OrderProjectionGapHealthIndicator implements HealthIndicator {
    private final OrderProjectionGapRepository gaps;
    public OrderProjectionGapHealthIndicator(OrderProjectionGapRepository gaps) { this.gaps = gaps; }
    @Override public Health health() {
        long open = gaps.countByStatus(OrderProjectionGapStatus.OPEN);
        long recovering = gaps.countByStatus(OrderProjectionGapStatus.RECOVERING);
        long retry = gaps.countByStatus(OrderProjectionGapStatus.RETRY_WAIT);
        long manual = gaps.countByStatus(OrderProjectionGapStatus.MANUAL_REVIEW);
        Health.Builder builder = manual > 0 ? Health.status("DEGRADED") : Health.up();
        return builder.withDetail("open", open).withDetail("recovering", recovering)
                .withDetail("retryWait", retry).withDetail("manualReview", manual).build();
    }
}
