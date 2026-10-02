package com.htv.smartfarm.order.readmodel;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("orderProjection")
@ConditionalOnProperty(prefix = "smartfarm.order.readmodel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrderProjectionHealthIndicator implements HealthIndicator {
    private final OrderProjectionInboxRepository inbox;
    public OrderProjectionHealthIndicator(OrderProjectionInboxRepository inbox) { this.inbox = inbox; }
    @Override public Health health() {
        try {
            long waiting = inbox.countByStatus(OrderProjectionInboxStatus.WAITING_GAP);
            long dead = inbox.countByStatus(OrderProjectionInboxStatus.DEAD);
            Health.Builder result = dead > 0 ? Health.status("DEGRADED") : Health.up();
            return result.withDetail("waitingGap", waiting).withDetail("dead", dead).build();
        } catch (RuntimeException failure) {
            return Health.down(failure).build();
        }
    }
}
