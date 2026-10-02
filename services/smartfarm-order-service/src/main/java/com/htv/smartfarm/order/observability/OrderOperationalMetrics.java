package com.htv.smartfarm.order.observability;

import com.htv.smartfarm.order.outbox.*;
import com.htv.smartfarm.order.readmodel.recovery.*;
import com.htv.smartfarm.order.saga.persistence.*;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.DoubleSupplier;
import org.springframework.stereotype.Component;

@Component
public class OrderOperationalMetrics {
    private final Clock clock;
    public OrderOperationalMetrics(MeterRegistry registry, OrderSagaJpaRepository sagas,
            OrderOutboxJpaRepository outbox, OrderProjectionGapRepository gaps,
            OrderSagaProperties sagaProperties, OrderOutboxProperties outboxProperties,
            OrderProjectionGapRecoveryProperties gapProperties, Clock clock) {
        this.clock = clock;
        gauge(registry, "smartfarm.order.saga.active", () -> sumSaga(sagas));
        gauge(registry, "smartfarm.order.saga.failed", () -> sagas.countByStatus(OrderSagaStatus.FAILED));
        gauge(registry, "smartfarm.order.saga.manual_review", () ->
                sagas.countByStatus(OrderSagaStatus.MANUAL_REVIEW)
                + sagas.countByStatus(OrderSagaStatus.WAITING_MANUAL_REVIEW));
        gauge(registry, "smartfarm.order.saga.stale_claims", () -> sagas.countStaleClaims(
                List.of(OrderSagaStatus.RUNNING, OrderSagaStatus.COMPENSATING),
                clock.instant().minus(sagaProperties.claimTimeout())));
        gauge(registry, "smartfarm.order.saga.oldest_active.seconds", () -> ageSeconds(
                sagas.oldestActive(List.of(OrderSagaStatus.PENDING, OrderSagaStatus.RUNNING,
                        OrderSagaStatus.WAITING_MANUAL_REVIEW, OrderSagaStatus.COMPENSATING))));

        gauge(registry, "smartfarm.order.outbox.pending", () -> sumOutbox(outbox));
        gauge(registry, "smartfarm.order.outbox.dead", () -> outbox.countByStatus(OrderOutboxStatus.DEAD));
        gauge(registry, "smartfarm.order.outbox.stale_claims", () -> outbox.countStaleClaims(
                OrderOutboxStatus.PUBLISHING, clock.instant().minus(outboxProperties.publishingTimeout())));
        gauge(registry, "smartfarm.order.outbox.oldest_pending.seconds", () -> ageMillis(
                outbox.oldestPendingCreatedAt(List.of(OrderOutboxStatus.NEW,
                        OrderOutboxStatus.PUBLISHING, OrderOutboxStatus.FAILED))));

        gauge(registry, "smartfarm.order.projection.gaps.pending", () -> sumGaps(gaps));
        gauge(registry, "smartfarm.order.projection.gaps.manual_review", () ->
                gaps.countByStatus(OrderProjectionGapStatus.MANUAL_REVIEW));
        gauge(registry, "smartfarm.order.projection.gaps.stale_claims", () -> gaps.countStaleClaims(
                OrderProjectionGapStatus.RECOVERING, clock.instant().minus(gapProperties.claimTimeout())));
        gauge(registry, "smartfarm.order.projection.gaps.oldest.seconds", () -> ageSeconds(
                gaps.oldestUnresolved(List.of(OrderProjectionGapStatus.OPEN,
                        OrderProjectionGapStatus.RECOVERING, OrderProjectionGapStatus.RETRY_WAIT))));
    }
    private static void gauge(MeterRegistry registry, String name, DoubleSupplier supplier) {
        Gauge.builder(name, supplier, DoubleSupplier::getAsDouble).register(registry);
    }
    private static long sumSaga(OrderSagaJpaRepository repository) {
        return List.of(OrderSagaStatus.PENDING, OrderSagaStatus.RUNNING,
                OrderSagaStatus.WAITING_MANUAL_REVIEW, OrderSagaStatus.COMPENSATING)
                .stream().mapToLong(repository::countByStatus).sum();
    }
    private static long sumOutbox(OrderOutboxJpaRepository repository) {
        return List.of(OrderOutboxStatus.NEW, OrderOutboxStatus.PUBLISHING, OrderOutboxStatus.FAILED)
                .stream().mapToLong(repository::countByStatus).sum();
    }
    private static long sumGaps(OrderProjectionGapRepository repository) {
        return List.of(OrderProjectionGapStatus.OPEN, OrderProjectionGapStatus.RECOVERING,
                OrderProjectionGapStatus.RETRY_WAIT).stream().mapToLong(repository::countByStatus).sum();
    }
    private double ageSeconds(Instant value) {
        return value == null ? 0 : Math.max(0, Duration.between(value, clock.instant()).toSeconds());
    }
    private double ageMillis(Long value) {
        return value == null ? 0 : Math.max(0, (clock.millis() - value) / 1000.0);
    }
}
