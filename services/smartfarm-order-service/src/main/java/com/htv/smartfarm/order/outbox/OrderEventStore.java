package com.htv.smartfarm.order.outbox;

import java.time.Clock;
import java.util.UUID;
import com.htv.smartfarm.order.domain.OrderEntity;
import com.htv.smartfarm.order.readmodel.recovery.OrderEventArchiveEntity;
import com.htv.smartfarm.order.readmodel.recovery.OrderEventArchiveRepository;
import org.springframework.stereotype.Service;

@Service
public class OrderEventStore {
    private final OrderOutboxJpaRepository outbox;
    private final OrderEventArchiveRepository archive;
    private final Clock clock;

    public OrderEventStore(OrderOutboxJpaRepository outbox,
            OrderEventArchiveRepository archive, Clock clock) {
        this.outbox = outbox;
        this.archive = archive;
        this.clock = clock;
    }

    /** Snapshot event used by the ordered projection and its archive replay. */
    public void append(OrderEntity order, String correlationId) {
        String eventId = UUID.randomUUID().toString();
        long createdAt = clock.millis();
        OrderOutboxEntity delivery = OrderOutboxEntity.snapshot(
                eventId, order, "order-changed.v1", correlationId, createdAt);
        outbox.save(delivery);
        archive.save(new OrderEventArchiveEntity(
                delivery.getEventId(), delivery.getTenantId(), delivery.getAggregateId(),
                delivery.getAggregateVersion(), delivery.getEventType(), delivery.getCorrelationId(),
                delivery.getCreatedAt(), delivery.getFarmId(), delivery.getBatchId(),
                delivery.getOrderStatus(), delivery.getCurrencyCode(), delivery.getTotalMinor(),
                delivery.getFailureReason()));
    }

    /** Operational event. It is intentionally not inserted into the projection archive. */
    public void appendBusiness(OrderEntity order, OrderBusinessEventType type,
            String correlationId, OrderBusinessEventContext context) {
        outbox.save(OrderOutboxEntity.business(UUID.randomUUID().toString(), order,
                type, correlationId, clock.millis(), context));
    }

    public void appendTransition(OrderEntity order, OrderBusinessEventType type,
            String correlationId, String actorId, String sagaId, String stepKey,
            String previousStatus, String newStatus) {
        appendBusiness(order, type, correlationId,
                OrderBusinessEventContext.transition(actorId, sagaId, stepKey,
                        previousStatus, newStatus));
    }
}
