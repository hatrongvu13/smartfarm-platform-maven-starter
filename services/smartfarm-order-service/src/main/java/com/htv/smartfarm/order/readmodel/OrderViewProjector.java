package com.htv.smartfarm.order.readmodel;

import com.htv.smartfarm.proto.events.v1.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class OrderViewProjector {
    private static final Logger log = LoggerFactory.getLogger(OrderViewProjector.class);
    private final OrderProjectionApplyTransaction transactions;
    public OrderViewProjector(OrderProjectionApplyTransaction transactions) {
        this.transactions = transactions;
    }
    public void apply(byte[] payload) {
        DomainEvent event;
        try { event = DomainEvent.parseFrom(payload); }
        catch (Exception invalid) {
            log.debug("OrderChanged projector dropped invalid payload: {}", invalid.getClass().getSimpleName());
            return;
        }
        if (!event.hasOrderChanged()) return;
        var metadata = event.getMetadata();
        var order = event.getOrderChanged().getOrder();
        if (metadata.getEventId().isBlank() || metadata.getTenantId().isBlank()
                || metadata.getAggregateId().isBlank() || metadata.getAggregateVersion() < 1
                || order.getOrderId().isBlank()
                || !metadata.getAggregateId().equals(order.getOrderId())) {
            log.warn("OrderChanged projector rejected invalid envelope");
            return;
        }
        try { transactions.receive(event, payload); }
        catch (DataIntegrityViolationException duplicateRace) {
            log.debug("OrderChanged duplicate event ignored: eventId={}", metadata.getEventId());
        }
    }
}
