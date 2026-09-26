package com.htv.smartfarm.health.outbox;

import java.util.UUID;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev & !prod")
@ConditionalOnProperty(prefix = "smartfarm.health.outbox", name = "enabled", havingValue = "true")
public final class PahoHealthEventPublisher implements HealthEventPublisher, AutoCloseable {
    private final String brokerUrl;
    private MqttClient client;

    public PahoHealthEventPublisher(@Value("${smartfarm.health.mqtt.url:tcp://localhost:1883}") String brokerUrl) {
        if (!brokerUrl.equals("tcp://localhost:1883")
                && !brokerUrl.equals("tcp://127.0.0.1:1883")) {
            throw new IllegalArgumentException("Development publisher requires loopback MQTT broker");
        }
        this.brokerUrl = brokerUrl;
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
        MqttMessage message = new MqttMessage(payload);
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
