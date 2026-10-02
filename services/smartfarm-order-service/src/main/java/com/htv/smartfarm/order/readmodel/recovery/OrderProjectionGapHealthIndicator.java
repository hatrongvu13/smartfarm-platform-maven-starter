package com.htv.smartfarm.order.readmodel.recovery;

import com.htv.smartfarm.order.observability.OrderObservabilityProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("orderProjectionGapRecovery")
@ConditionalOnProperty(prefix="smartfarm.order.readmodel.gap-recovery",name="enabled",havingValue="true")
public class OrderProjectionGapHealthIndicator implements HealthIndicator {
    private final OrderProjectionGapRepository gaps; private final OrderProjectionGapRecoveryProperties worker;
    private final OrderObservabilityProperties thresholds; private final Clock clock;
    public OrderProjectionGapHealthIndicator(OrderProjectionGapRepository gaps,
            OrderProjectionGapRecoveryProperties worker, OrderObservabilityProperties thresholds, Clock clock) {
        this.gaps=gaps; this.worker=worker; this.thresholds=thresholds; this.clock=clock;
    }
    @Override public Health health() {
        try {
            long open=gaps.countByStatus(OrderProjectionGapStatus.OPEN);
            long recovering=gaps.countByStatus(OrderProjectionGapStatus.RECOVERING);
            long retry=gaps.countByStatus(OrderProjectionGapStatus.RETRY_WAIT);
            long manual=gaps.countByStatus(OrderProjectionGapStatus.MANUAL_REVIEW);
            long unresolved=open+recovering+retry;
            Instant oldest=gaps.oldestUnresolved(List.of(OrderProjectionGapStatus.OPEN,
                    OrderProjectionGapStatus.RECOVERING,OrderProjectionGapStatus.RETRY_WAIT));
            long age=oldest==null?0:Math.max(0,Duration.between(oldest,clock.instant()).toSeconds());
            long stale=gaps.countStaleClaims(OrderProjectionGapStatus.RECOVERING,
                    clock.instant().minus(worker.claimTimeout()));
            boolean degraded=manual>0 || stale>0 || unresolved>=thresholds.projectionGapWarning()
                    || age>=thresholds.projectionOldestGapWarning().toSeconds();
            Health.Builder result=degraded?Health.status("DEGRADED"):Health.up();
            return result.withDetail("open",open).withDetail("recovering",recovering)
                    .withDetail("retryWait",retry).withDetail("manualReview",manual)
                    .withDetail("unresolved",unresolved).withDetail("staleClaims",stale)
                    .withDetail("oldestUnresolvedAgeSeconds",age).build();
        } catch (RuntimeException failure) { return Health.down(failure).build(); }
    }
}
