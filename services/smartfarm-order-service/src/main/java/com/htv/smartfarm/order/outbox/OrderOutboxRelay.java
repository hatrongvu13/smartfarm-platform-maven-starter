package com.htv.smartfarm.order.outbox;

import com.htv.smartfarm.messaging.dispatch.DefaultDispatchFailureClassifier;
import com.htv.smartfarm.messaging.dispatch.DispatchErrorCodes;
import com.htv.smartfarm.messaging.dispatch.DispatchFailureClassifier;
import com.htv.smartfarm.messaging.dispatch.DispatchFailureDecision;
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
    // Shared classifier: MqttException -> infra (retry+break), IllegalArgumentException (bad topic
    // from OrderEventMapper) -> permanent-data (dead+continue), else -> unknown (retry+continue).
    private final DispatchFailureClassifier classifier = new DefaultDispatchFailureClassifier();

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
                DispatchFailureDecision decision = classifier.classify(exception);
                String code = DispatchErrorCodes.codeOf(exception, 120);
                if (decision.markDead()) {
                    // Poison (bad topic/payload): dead-letter now, keep processing the batch.
                    transactions.markDead(row.eventId(), code);
                    log.warn("Order outbox event dead-lettered (permanent): eventId={} category={} reason={}",
                            row.eventId(), decision.category(), code);
                } else {
                    transactions.markFailed(row.eventId(), code);
                    log.warn("Order outbox publish deferred: eventId={} category={} reason={}",
                            row.eventId(), decision.category(), code);
                }
                if (decision.breakBatch()) {
                    // Infrastructure failure (broker down): stop the batch, back off, retry next poll.
                    break;
                }
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
