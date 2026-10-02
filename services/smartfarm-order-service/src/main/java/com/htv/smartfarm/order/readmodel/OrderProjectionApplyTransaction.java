package com.htv.smartfarm.order.readmodel;

import java.time.Clock;
import com.htv.smartfarm.proto.events.v1.DomainEvent;
import com.htv.smartfarm.proto.order.v1.FarmOrder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderProjectionApplyTransaction {
    private final OrderProjectionInboxRepository inbox;
    private final OrderViewJpaRepository views;
    private final Clock clock;
    public OrderProjectionApplyTransaction(OrderProjectionInboxRepository inbox,
            OrderViewJpaRepository views, Clock clock) {
        this.inbox = inbox; this.views = views; this.clock = clock;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void receive(DomainEvent event, byte[] payload) {
        var metadata = event.getMetadata();
        if (inbox.existsById(metadata.getEventId())) return;
        var row = new OrderProjectionInboxEntity(metadata.getEventId(), metadata.getTenantId(),
                metadata.getAggregateId(), metadata.getAggregateVersion(), payload, clock.instant());
        inbox.saveAndFlush(row);
        drain(metadata.getTenantId(), metadata.getAggregateId());
    }
    private void drain(String tenantId, String orderId) {
        while (true) {
            OrderView view = views.findByTenantIdAndOrderIdForUpdate(tenantId, orderId).orElse(null);
            long expected = view == null ? 1 : view.getLastAggregateVersion() + 1;
            var next = inbox.findVersionForUpdate(tenantId, orderId, expected,
                    OrderProjectionInboxStatus.WAITING_GAP).orElse(null);
            if (next == null) return;
            DomainEvent event;
            try { event = DomainEvent.parseFrom(next.getPayload()); }
            catch (Exception invalid) { next.dead("PAYLOAD_INVALID", clock.instant()); return; }
            FarmOrder order = event.getOrderChanged().getOrder();
            long occurredAt = event.getMetadata().hasOccurredAt()
                    ? event.getMetadata().getOccurredAt().getSeconds() * 1000
                        + event.getMetadata().getOccurredAt().getNanos() / 1_000_000
                    : clock.millis();
            String failure = order.getFailureReason().isBlank() ? null : order.getFailureReason();
            if (view == null) {
                view = new OrderView(order.getOrderId(), tenantId, order.getFarmId(), order.getStatus().name(),
                        order.getTotal().getCurrencyCode(), order.getTotal().getMinorUnits(), failure,
                        occurredAt, expected, event.getMetadata().getEventId());
                views.save(view);
            } else {
                view.apply(order.getFarmId(), order.getStatus().name(), order.getTotal().getCurrencyCode(),
                        order.getTotal().getMinorUnits(), failure, occurredAt, expected,
                        event.getMetadata().getEventId());
            }
            next.applied(clock.instant());
        }
    }
}
