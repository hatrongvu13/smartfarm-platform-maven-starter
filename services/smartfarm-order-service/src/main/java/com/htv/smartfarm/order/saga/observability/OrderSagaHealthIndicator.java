package com.htv.smartfarm.order.saga.observability;

import com.htv.smartfarm.order.observability.OrderObservabilityProperties;
import com.htv.smartfarm.order.saga.persistence.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("orderSaga")
@ConditionalOnProperty(prefix="smartfarm.order.saga",name="enabled",havingValue="true")
public class OrderSagaHealthIndicator implements HealthIndicator {
    private final OrderSagaJpaRepository sagas; private final OrderSagaProperties worker;
    private final OrderObservabilityProperties thresholds; private final Clock clock;
    public OrderSagaHealthIndicator(OrderSagaJpaRepository sagas, OrderSagaProperties worker,
            OrderObservabilityProperties thresholds, Clock clock) {
        this.sagas=sagas; this.worker=worker; this.thresholds=thresholds; this.clock=clock;
    }
    @Override public Health health() {
        try {
            long pending=sagas.countByStatus(OrderSagaStatus.PENDING);
            long running=sagas.countByStatus(OrderSagaStatus.RUNNING);
            long waiting=sagas.countByStatus(OrderSagaStatus.WAITING_MANUAL_REVIEW);
            long compensating=sagas.countByStatus(OrderSagaStatus.COMPENSATING);
            long failed=sagas.countByStatus(OrderSagaStatus.FAILED);
            long manual=sagas.countByStatus(OrderSagaStatus.MANUAL_REVIEW);
            List<OrderSagaStatus> active=List.of(OrderSagaStatus.PENDING,OrderSagaStatus.RUNNING,
                    OrderSagaStatus.WAITING_MANUAL_REVIEW,OrderSagaStatus.COMPENSATING);
            Instant oldest=sagas.oldestActive(active);
            long age=oldest==null?0:Math.max(0,Duration.between(oldest,clock.instant()).toSeconds());
            long stale=sagas.countStaleClaims(List.of(OrderSagaStatus.RUNNING,OrderSagaStatus.COMPENSATING),
                    clock.instant().minus(worker.claimTimeout()));
            boolean degraded=waiting>0 || failed>0 || manual>0 || stale>0
                    || age>=thresholds.sagaOldestActiveWarning().toSeconds();
            Health.Builder result=degraded?Health.status("DEGRADED"):Health.up();
            return result.withDetail("pending",pending).withDetail("running",running)
                    .withDetail("waitingManualReview",waiting).withDetail("compensating",compensating)
                    .withDetail("failed",failed).withDetail("manualReview",manual)
                    .withDetail("staleClaims",stale).withDetail("oldestActiveAgeSeconds",age).build();
        } catch (RuntimeException failure) { return Health.down(failure).build(); }
    }
}
