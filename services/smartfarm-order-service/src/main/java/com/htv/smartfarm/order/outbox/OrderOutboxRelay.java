package com.htv.smartfarm.order.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "smartfarm.outbox", name = "enabled", havingValue = "true")
public class OrderOutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OrderOutboxRelay.class);
    private final OrderOutboxTransactionService transactions;
    private final OrderMqttPublisher publisher;
    public OrderOutboxRelay(OrderOutboxTransactionService transactions, OrderMqttPublisher publisher) {
        this.transactions = transactions; this.publisher = publisher;
    }
    @Scheduled(fixedDelayString = "${smartfarm.outbox.poll-ms:2000}")
    public void poll() {
        for (OrderOutboxEvent row : transactions.claimBatch()) {
            try {
                publisher.publish(OrderEventMapper.topic(row), OrderEventMapper.event(row).toByteArray());
                transactions.markPublished(row.eventId());
                log.info("Order outbox event published: eventId={} type={}", row.eventId(), row.eventType());
            } catch (Exception exception) {
                transactions.markFailed(row.eventId(), exception.getClass().getSimpleName());
                log.warn("Order outbox publish deferred: eventId={} reason={}",
                        row.eventId(), exception.getClass().getSimpleName());
                break;
            }
        }
    }
    @Scheduled(fixedDelayString = "${smartfarm.outbox.maintenance-ms:60000}")
    public void maintenance() {
        int recovered = transactions.recoverStaleClaims();
        int deleted = transactions.cleanupPublished();
        if (recovered > 0 || deleted > 0) {
            log.info("Order outbox maintenance: recovered={} deleted={}", recovered, deleted);
        }
    }
}
