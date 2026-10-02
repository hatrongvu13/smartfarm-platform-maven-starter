package com.htv.smartfarm.order.readmodel;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Subscribes to {@code order-changed} integration events on MQTT and maintains the
 * {@link OrderView} read-model (CQRS projection). This is an idempotent, replay-safe consumer:
 * an event older than what the projection already holds ({@code occurredAt <= lastEventAt}) is
 * ignored, so redelivery or out-of-order arrival cannot corrupt the view.
 *
 * <p>Gated on {@code smartfarm.order.readmodel.enabled} (default true). Single-instance DEV
 * consumer; a production build would use a durable subscription + inbox dedup table.
 */
@Component
@ConditionalOnProperty(prefix = "smartfarm.order.readmodel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrderChangedConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderChangedConsumer.class);

    private final OrderViewJpaRepository views;
    private final OrderViewProjector projector;
    private final String url;
    private final String username;
    private final String password;
    private final String topicFilter;
    private final String clientId;
    private MqttClient client;

    public OrderChangedConsumer(OrderViewJpaRepository views,
                                OrderViewProjector projector,
                                @Value("${smartfarm.mqtt.url:tcp://localhost:1883}") String url,
                                @Value("${smartfarm.mqtt.username:}") String username,
                                @Value("${smartfarm.mqtt.password:}") String password,
                                @Value("${smartfarm.order.readmodel.topic-filter:smartfarm/+/+/domain/order-changed/+}") String topicFilter,
                                @Value("${smartfarm.order.readmodel.client-id:smartfarm-order-readmodel-client}") String clientId) {
        this.views = views;
        this.projector = projector;
        this.url = url;
        this.username = username;
        this.password = password;
        this.topicFilter = topicFilter;
        this.clientId = clientId;
    }

    @PostConstruct
    public void start() {
        try {
            client = new MqttClient(url, clientId, new MemoryPersistence());
            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setCleanSession(false);
            opts.setConnectionTimeout(3);
            opts.setAutomaticReconnect(true);
            if (!username.isBlank()) {
                opts.setUserName(username);
                opts.setPassword(password.toCharArray());
            }
            client.setCallback(new MqttCallbackExtended() {
                @Override public void connectComplete(boolean reconnect, String serverURI) {
                    try {
                        client.subscribe(topicFilter, 1);
                        log.info("OrderChanged consumer subscribed to '{}' (reconnect={})", topicFilter, reconnect);
                    } catch (MqttException e) {
                        log.warn("OrderChanged subscribe failed: {}", e.getMessage());
                    }
                }
                @Override public void connectionLost(Throwable cause) {
                    log.warn("OrderChanged consumer connection lost: {}", cause == null ? "?" : cause.getMessage());
                }
                @Override public void messageArrived(String topic, MqttMessage message) {
                    projector.apply(message.getPayload());
                }
                @Override public void deliveryComplete(IMqttDeliveryToken token) { }
            });
            try {
                client.connect(opts);
            } catch (MqttException deferred) {
                log.warn("OrderChanged consumer connect deferred (will retry): {}", deferred.getMessage());
            }
        } catch (MqttException e) {
            log.warn("OrderChanged consumer init failed (read-model updates paused until broker up): {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        if (client != null) {
            try {
                if (client.isConnected()) client.disconnect();
                client.close();
            } catch (MqttException ignored) {
            }
        }
    }
}
