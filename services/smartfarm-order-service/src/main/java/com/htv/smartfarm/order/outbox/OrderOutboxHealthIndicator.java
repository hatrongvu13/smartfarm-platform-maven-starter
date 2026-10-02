package com.htv.smartfarm.order.outbox;

import com.htv.smartfarm.order.observability.OrderObservabilityProperties;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("orderOutbox")
@ConditionalOnProperty(prefix="smartfarm.outbox", name="enabled", havingValue="true")
public class OrderOutboxHealthIndicator implements HealthIndicator {
    private final OrderOutboxJpaRepository outbox; private final OrderOutboxProperties relay;
    private final OrderObservabilityProperties thresholds; private final Clock clock;
    public OrderOutboxHealthIndicator(OrderOutboxJpaRepository outbox, OrderOutboxProperties relay,
            OrderObservabilityProperties thresholds, Clock clock) {
        this.outbox=outbox; this.relay=relay; this.thresholds=thresholds; this.clock=clock;
    }
    @Override public Health health() {
        try {
            long fresh=outbox.countByStatus(OrderOutboxStatus.NEW);
            long failed=outbox.countByStatus(OrderOutboxStatus.FAILED);
            long publishing=outbox.countByStatus(OrderOutboxStatus.PUBLISHING);
            long dead=outbox.countByStatus(OrderOutboxStatus.DEAD);
            long pending=fresh+failed+publishing;
            Long oldest=outbox.oldestPendingCreatedAt(List.of(OrderOutboxStatus.NEW,OrderOutboxStatus.FAILED,OrderOutboxStatus.PUBLISHING));
            long age=oldest==null?0:Math.max(0,(clock.millis()-oldest)/1000);
            long stale=outbox.countStaleClaims(OrderOutboxStatus.PUBLISHING,clock.instant().minus(relay.publishingTimeout()));
            boolean degraded=dead>0 || stale>0 || pending>=thresholds.outboxPendingWarning()
                    || age>=thresholds.outboxOldestPendingWarning().toSeconds();
            Health.Builder result=degraded?Health.status("DEGRADED"):Health.up();
            return result.withDetail("new",fresh).withDetail("publishing",publishing)
                    .withDetail("failed",failed).withDetail("dead",dead).withDetail("pending",pending)
                    .withDetail("staleClaims",stale).withDetail("oldestPendingAgeSeconds",age).build();
        } catch (RuntimeException failure) {
            return Health.down(failure).build();
        }
    }
}
