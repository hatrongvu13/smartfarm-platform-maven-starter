package com.htv.smartfarm.identity.messaging.outbox;

import com.htv.smartfarm.identity.messaging.mqtt.IdentityMqttConnectionManager;
import com.htv.smartfarm.identity.messaging.mqtt.IdentityMqttProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "smartfarm.identity.outbox",
        name = "enabled",
        havingValue = "true"
)
public class IdentityOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(IdentityOutboxRelay.class);

    private final IdentityOutboxTransactionService transactions;
    private final IdentityMqttConnectionManager mqtt;
    private final IdentityMqttProperties mqttProperties;

    public IdentityOutboxRelay(
            IdentityOutboxTransactionService transactions,
            IdentityMqttConnectionManager mqtt,
            IdentityMqttProperties mqttProperties
    ) {
        this.transactions = transactions;
        this.mqtt = mqtt;
        this.mqttProperties = mqttProperties;
    }

    @Scheduled(fixedDelayString = "${smartfarm.identity.outbox.poll-interval:2s}")
    public void relay() {
        if (!mqtt.snapshot().connected()) return;
        for (IdentityOutboxMessage message : transactions.claimBatch(mqttProperties.qos())) {
            try {
                mqtt.publish(message.topic(), message.payload(), message.qos());
                transactions.markPublished(message.eventId());
                log.info(
                        "Identity outbox published: eventId={}, attempt={}",
                        message.eventId(), message.attemptCount() + 1
                );
            } catch (RuntimeException | org.eclipse.paho.client.mqttv3.MqttException exception) {
                transactions.markFailed(message.eventId(), exception.getClass().getSimpleName());
                log.warn(
                        "Identity outbox publish deferred: eventId={}, error={}",
                        message.eventId(), exception.getClass().getSimpleName()
                );
                break;
            }
        }
    }

    @Scheduled(fixedDelayString = "${smartfarm.identity.outbox.cleanup-interval:1h}")
    public void maintenance() {
        int recovered = transactions.recoverStaleClaims();
        int deleted = transactions.cleanupPublished();
        if (recovered > 0 || deleted > 0) {
            log.info("Identity outbox maintenance: recovered={}, deleted={}", recovered, deleted);
        }
    }
}
