package com.htv.smartfarm.identity.messaging.outbox;

import com.htv.smartfarm.identity.messaging.mqtt.IdentityMqttConnectionManager;
import com.htv.smartfarm.identity.messaging.mqtt.IdentityMqttProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "smartfarm.identity.outbox",
        name = "enabled",
        havingValue = "true"
)
// The relay publishes to MQTT via IdentityMqttConnectionManager, which itself only exists when
// smartfarm.identity.mqtt.enabled=true. We make that dependency OPTIONAL (ObjectProvider) so
// turning MQTT off cleanly leaves the relay inert (nothing to publish through), instead of
// failing context startup on a missing connection-manager bean.
public class IdentityOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(IdentityOutboxRelay.class);

    private final IdentityOutboxTransactionService transactions;
    private final ObjectProvider<IdentityMqttConnectionManager> mqttProvider;
    private final IdentityMqttProperties mqttProperties;

    public IdentityOutboxRelay(
            IdentityOutboxTransactionService transactions,
            ObjectProvider<IdentityMqttConnectionManager> mqttProvider,
            IdentityMqttProperties mqttProperties
    ) {
        this.transactions = transactions;
        this.mqttProvider = mqttProvider;
        this.mqttProperties = mqttProperties;
    }

    @Scheduled(fixedDelayString = "${smartfarm.identity.outbox.poll-interval:2s}")
    public void relay() {
        IdentityMqttConnectionManager mqtt = mqttProvider.getIfAvailable();
        // MQTT disabled (no connection manager bean) -> nothing to publish through; leave the
        // outbox rows for a later run when MQTT is enabled.
        if (mqtt == null || !mqtt.snapshot().connected()) return;
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
