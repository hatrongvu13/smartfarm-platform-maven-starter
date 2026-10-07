package com.htv.smartfarm.health.outbox;

import java.util.UUID;

import com.htv.smartfarm.security.mqtt.MqttSecurityVerifier;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Paho-based health event publisher. Active in every profile (gated by
 * {@code smartfarm.health.outbox.enabled}); it signs each frame via {@link MqttSecurityVerifier}
 * before publishing. The broker URL comes from {@code smartfarm.health.mqtt.url} — loopback in dev,
 * the real broker in prod. An empty URL is rejected so a misconfigured prod never silently no-ops.
 */
@Component
@ConditionalOnProperty(prefix = "smartfarm.health.outbox", name = "enabled", havingValue = "true")
public final class PahoHealthEventPublisher implements HealthEventPublisher, AutoCloseable {
    private final String brokerUrl;
    private final MqttSecurityVerifier mqttSecurity;
    private MqttClient client;

    public PahoHealthEventPublisher(@Value("${smartfarm.health.mqtt.url:tcp://localhost:1883}") String brokerUrl,
                                    MqttSecurityVerifier mqttSecurity) {
        if (brokerUrl == null || brokerUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "smartfarm.health.mqtt.url must be set when the outbox relay is enabled");
        }
        this.brokerUrl = brokerUrl;
        this.mqttSecurity = mqttSecurity;
    }

    @Override
    public synchronized void publish(String topic, byte[] payload) throws Exception {
        if (client == null) {
            client = new MqttClient(brokerUrl, "smartfarm-health-" + UUID.randomUUID(), new MemoryPersistence());
            client.setTimeToWait(5000);
        }
        if (!client.isConnected()) {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setConnectionTimeout(3);
            client.connect(options);
        }
        MqttMessage message = new MqttMessage(mqttSecurity.sign(topic, payload));
        message.setQos(1);
        message.setRetained(false);
        client.publish(topic, message);
    }

    @Override
    public synchronized void close() throws Exception {
        if (client != null) {
            try {
                if (client.isConnected()) client.disconnect();
            } finally {
                client.close();
                client = null;
            }
        }
    }
}
