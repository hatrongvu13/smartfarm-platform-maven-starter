package com.htv.smartfarm.gateway.events;

import com.google.protobuf.util.JsonFormat;
import com.htv.smartfarm.proto.events.v1.DomainEvent;
import com.htv.smartfarm.security.mqtt.MqttSecurityVerifier;

import java.util.UUID;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Subscribes to all SmartFarm domain events on MQTT and republishes them onto the in-process
 * {@link DomainEventBus} for delivery to WebSocket clients. Topics are
 * {@code smartfarm/<tenant>/<farm>/domain/<event>/v1}; tenant + farm are extracted from the topic
 * so the WebSocket layer can filter per authenticated tenant. Payloads are protobuf
 * {@link DomainEvent}; we render them to JSON for the browser.
 *
 * <p>Connect lazily/reconnect automatically so a broker outage never blocks gateway startup.
 * Gated on {@code smartfarm.events.mqtt.enabled} (default true).
 */
@Component
@ConditionalOnProperty(prefix = "smartfarm.events.mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MqttEventSubscriber {

    private static final Logger log = LoggerFactory.getLogger(MqttEventSubscriber.class);

    private final DomainEventBus bus;
    private final MqttSecurityVerifier mqttSecurity;
    private final String url;
    private final String username;
    private final String password;
    private final String topicFilter;
    private MqttClient client;

    public MqttEventSubscriber(DomainEventBus bus,
                               MqttSecurityVerifier mqttSecurity,
                               @Value("${smartfarm.mqtt.url:tcp://localhost:1883}") String url,
                               @Value("${smartfarm.mqtt.username:}") String username,
                               @Value("${smartfarm.mqtt.password:}") String password,
                               @Value("${smartfarm.events.mqtt.topic-filter:smartfarm/+/+/domain/#}") String topicFilter) {
        this.bus = bus;
        this.mqttSecurity = mqttSecurity;
        this.url = url;
        this.username = username;
        this.password = password;
        this.topicFilter = topicFilter;
    }

    @PostConstruct
    public void start() {
        try {
            client = new MqttClient(url, "smartfarm-gateway-ws-" + UUID.randomUUID(), new MemoryPersistence());
            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setCleanSession(true);
            opts.setConnectionTimeout(3);
            opts.setAutomaticReconnect(true);
            if (!username.isBlank()) {
                opts.setUserName(username);
                opts.setPassword(password.toCharArray());
            }
            client.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    try {
                        client.subscribe(topicFilter, 1);
                        log.info("WS bridge subscribed to MQTT topic filter '{}' (reconnect={})", topicFilter, reconnect);
                    } catch (MqttException e) {
                        log.warn("WS bridge subscribe failed: {}", e.getMessage());
                    }
                }

                @Override
                public void connectionLost(Throwable cause) {
                    log.warn("WS bridge MQTT connection lost: {}", cause == null ? "?" : cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    onMessage(topic, message.getPayload());
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                }
            });
            // connect() is best-effort; automaticReconnect retries in the background.
            try {
                client.connect(opts);
            } catch (MqttException connectDeferred) {
                log.warn("WS bridge MQTT connect deferred (will retry): {}", connectDeferred.getMessage());
            }
        } catch (MqttException e) {
            log.warn("WS bridge MQTT init failed (events disabled until broker up): {}", e.getMessage());
        }
    }

    private void onMessage(String topic, byte[] payload) {
        try {
            MqttSecurityVerifier.Result verification = mqttSecurity.verify(topic, payload);
            if (!verification.accepted()) {
                log.warn("WS bridge dropped MQTT message on {}: {}", topic, verification.reason());
                return;
            }
            DomainEvent evt = DomainEvent.parseFrom(verification.payload());
            String json = JsonFormat.printer().omittingInsignificantWhitespace().print(evt);
            String tenant = evt.getMetadata().getTenantId();
            String farm = evt.getMetadata().getFarmId();
            // Fallback to topic segments if metadata is empty: smartfarm/<tenant>/<farm>/domain/...
            if (tenant.isBlank() || farm.isBlank()) {
                String[] seg = topic.split("/");
                if (seg.length >= 3) {
                    if (tenant.isBlank()) tenant = seg[1];
                    if (farm.isBlank()) farm = seg[2];
                }
            }
            bus.publish(new DomainEventBus.Envelope(tenant, farm, topic, json));
        } catch (Exception e) {
            log.debug("WS bridge dropped a non-DomainEvent message on {}: {}", topic, e.getClass().getSimpleName());
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
