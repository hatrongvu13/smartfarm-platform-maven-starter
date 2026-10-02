package com.htv.smartfarm.order.readmodel;

import java.time.Clock;
import com.htv.smartfarm.proto.events.v1.DomainEvent;
import com.htv.smartfarm.proto.order.v1.FarmOrder;
import org.springframework.stereotype.Service;

@Service
public class OrderProjectionDrainService {
    private final OrderProjectionInboxRepository inbox;
    private final OrderViewJpaRepository views;
    private final Clock clock;
    public OrderProjectionDrainService(OrderProjectionInboxRepository inbox,
            OrderViewJpaRepository views, Clock clock) {
        this.inbox = inbox; this.views = views; this.clock = clock;
    }
    public OrderProjectionDrainResult drain(String tenantId, String orderId) {
        OrderView initial = views.findByTenantIdAndOrderIdForUpdate(tenantId, orderId).orElse(null);
        long previous = initial == null ? 0 : initial.getLastAggregateVersion();
        int applied = 0;
        while (true) {
            OrderView view = views.findByTenantIdAndOrderIdForUpdate(tenantId, orderId).orElse(null);
            long expected = view == null ? 1 : view.getLastAggregateVersion() + 1;
            var next = inbox.findVersionForUpdate(tenantId, orderId, expected,
                    OrderProjectionInboxStatus.WAITING_GAP).orElse(null);
            if (next == null) {
                long current = view == null ? 0 : view.getLastAggregateVersion();
                boolean gap = inbox.existsWaitingAfter(tenantId, orderId, current + 1,
                        OrderProjectionInboxStatus.WAITING_GAP);
                return new OrderProjectionDrainResult(previous, current, applied, gap);
            }
            DomainEvent event;
            try { event = DomainEvent.parseFrom(next.getPayload()); }
            catch (Exception invalid) {
                next.dead("PAYLOAD_INVALID", clock.instant());
                return new OrderProjectionDrainResult(previous,
                        view == null ? 0 : view.getLastAggregateVersion(), applied, true);
            }
            if (!event.hasOrderChanged()) {
                next.dead("ORDER_CHANGED_REQUIRED", clock.instant());
                return new OrderProjectionDrainResult(previous,
                        view == null ? 0 : view.getLastAggregateVersion(), applied, true);
            }
            FarmOrder order = event.getOrderChanged().getOrder();
            long occurredAt = event.getMetadata().hasOccurredAt()
                    ? event.getMetadata().getOccurredAt().getSeconds() * 1000
                        + event.getMetadata().getOccurredAt().getNanos() / 1_000_000
                    : clock.millis();
            String failure = order.getFailureReason().isBlank() ? null : order.getFailureReason();
            if (view == null) {
                views.saveAndFlush(new OrderView(order.getOrderId(), tenantId, order.getFarmId(),
                        order.getStatus().name(), order.getTotal().getCurrencyCode(),
                        order.getTotal().getMinorUnits(), failure, occurredAt, expected,
                        event.getMetadata().getEventId()));
            } else {
                view.apply(order.getFarmId(), order.getStatus().name(),
                        order.getTotal().getCurrencyCode(), order.getTotal().getMinorUnits(),
                        failure, occurredAt, expected, event.getMetadata().getEventId());
                views.flush();
            }
            next.applied(clock.instant());
            applied++;
        }
    }
}
