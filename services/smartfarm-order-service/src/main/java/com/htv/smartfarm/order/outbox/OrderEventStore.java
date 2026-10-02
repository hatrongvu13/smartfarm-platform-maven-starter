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
        this.outbox = outbox; this.archive = archive; this.clock = clock;
    }
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
}
