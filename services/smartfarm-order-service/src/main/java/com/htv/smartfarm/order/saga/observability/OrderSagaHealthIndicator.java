package com.htv.smartfarm.order.saga.observability;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import com.htv.smartfarm.order.saga.persistence.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("orderSaga")
@ConditionalOnProperty(prefix = "smartfarm.order.saga", name = "enabled", havingValue = "true")
public class OrderSagaHealthIndicator implements HealthIndicator {
    private final OrderSagaJpaRepository sagas;
    private final Clock clock;
    public OrderSagaHealthIndicator(OrderSagaJpaRepository sagas, Clock clock) {
        this.sagas = sagas; this.clock = clock;
    }
    @Override public Health health() {
        long pending = sagas.countByStatus(OrderSagaStatus.PENDING);
        long running = sagas.countByStatus(OrderSagaStatus.RUNNING);
        long compensating = sagas.countByStatus(OrderSagaStatus.COMPENSATING);
        long manualReview = sagas.countByStatus(OrderSagaStatus.MANUAL_REVIEW);
        Instant oldest = sagas.oldestActive(List.of(
                OrderSagaStatus.PENDING, OrderSagaStatus.RUNNING, OrderSagaStatus.COMPENSATING));
        long oldestAge = oldest == null ? 0 : Math.max(0, Duration.between(oldest, clock.instant()).toSeconds());
        Health.Builder result = manualReview > 0 ? Health.status("DEGRADED") : Health.up();
        return result.withDetail("pending", pending).withDetail("running", running)
                .withDetail("compensating", compensating).withDetail("manualReview", manualReview)
                .withDetail("oldestActiveAgeSeconds", oldestAge).build();
    }
}
