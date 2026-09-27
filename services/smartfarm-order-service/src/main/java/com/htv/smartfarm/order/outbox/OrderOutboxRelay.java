package com.htv.smartfarm.order.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Single-instance DEV relay. Publish outside DB transaction; ack before marking PUBLISHED. */
@Component
@ConditionalOnProperty(prefix = "smartfarm.outbox", name = "enabled", havingValue = "true")
public class OrderOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OrderOutboxRelay.class);

    private final OrderOutboxJpaRepository outbox;
    private final OrderMqttPublisher publisher;

    public OrderOutboxRelay(OrderOutboxJpaRepository outbox, OrderMqttPublisher publisher) {
        this.outbox = outbox;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${smartfarm.outbox.poll-ms:2000}")
    public void poll() {
        for (OrderOutboxEvent row : outbox.findPending(PageRequest.of(0, 50))) {
            try {
                publisher.publish(OrderEventMapper.topic(row), OrderEventMapper.event(row).toByteArray());
                markPublished(row.eventId());
                log.info("Order outbox event published: eventId={} type={}", row.eventId(), row.eventType());
            } catch (Exception ex) {
                // Leave NEW; retry with the SAME eventId. Do not expose payload or credentials in logs.
                log.warn("Order outbox publish deferred: eventId={} reason={}", row.eventId(), ex.getClass().getSimpleName());
                break; // broker unavailable: avoid tight-loop attempts across the batch
            }
        }
    }

    @Transactional
    public int markPublished(String eventId) {
        return outbox.findById(eventId)
                .filter(e -> "NEW".equals(e.getStatus()))
                .map(e -> {
                    e.markPublished();
                    outbox.save(e);
                    return 1;
                })
                .orElse(0);
    }
}
