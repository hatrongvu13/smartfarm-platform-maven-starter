package com.htv.smartfarm.order.readmodel;

import com.htv.smartfarm.proto.events.v1.DomainEvent;
import com.htv.smartfarm.proto.order.v1.FarmOrder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies an {@code OrderChanged} event to the {@link OrderView} projection in its own
 * transaction (separate bean so Spring's transactional proxy applies when called from the MQTT
 * callback). Idempotent + ordering-safe via {@code occurredAt} vs the view's {@code lastEventAt}.
 */
@Component
public class OrderViewProjector {

    private static final Logger log = LoggerFactory.getLogger(OrderViewProjector.class);

    private final OrderViewJpaRepository views;

    public OrderViewProjector(OrderViewJpaRepository views) {
        this.views = views;
    }

    @Transactional
    public void apply(byte[] payload) {
        DomainEvent evt;
        try {
            evt = DomainEvent.parseFrom(payload);
        } catch (Exception e) {
            log.debug("OrderChanged projector dropped non-DomainEvent payload: {}", e.getClass().getSimpleName());
            return;
        }
        if (!evt.hasOrderChanged()) return;
        FarmOrder o = evt.getOrderChanged().getOrder();
        String tenant = evt.getMetadata().getTenantId();
        long occurredAt = evt.getMetadata().hasOccurredAt()
                ? evt.getMetadata().getOccurredAt().getSeconds() * 1000 : System.currentTimeMillis();
        if (o.getOrderId().isBlank() || tenant.isBlank()) return;

        var existing = views.findByTenantIdAndOrderId(tenant, o.getOrderId()).orElse(null);
        if (existing != null && occurredAt <= existing.getLastEventAt()) {
            return; // stale / duplicate event — ignore
        }
        String currency = o.getTotal().getCurrencyCode();
        long total = o.getTotal().getMinorUnits();
        String failure = o.getFailureReason().isBlank() ? null : o.getFailureReason();
        if (existing == null) {
            views.save(new OrderView(o.getOrderId(), tenant, o.getFarmId(), o.getStatus().name(),
                    currency, total, failure, occurredAt));
        } else {
            existing.apply(o.getStatus().name(), currency, total, failure, occurredAt);
            views.save(existing);
        }
        log.debug("OrderView updated order_id={} status={}", o.getOrderId(), o.getStatus().name());
    }
}
