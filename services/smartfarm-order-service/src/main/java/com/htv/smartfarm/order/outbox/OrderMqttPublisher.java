package com.htv.smartfarm.order.outbox;

import java.util.UUID;

import com.htv.smartfarm.security.mqtt.MqttSecurityVerifier;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Synchronous QoS 1 publish. Connect lazily so broker outages do not block service startup. */
@Component
public class OrderMqttPublisher implements AutoCloseable {
    private final String url;
    private final String username;
    private final String password;
    private final MqttSecurityVerifier mqttSecurity;
    private MqttClient client;

    public OrderMqttPublisher(@Value("${smartfarm.mqtt.url:tcp://localhost:1883}") String url,
                              @Value("${smartfarm.mqtt.username:}") String username,
                              @Value("${smartfarm.mqtt.password:}") String password,
                              MqttSecurityVerifier mqttSecurity) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.mqttSecurity = mqttSecurity;
    }

    public synchronized void publish(String topic, byte[] payload) throws MqttException {
        if (client == null) {
            client = new MqttClient(url, "smartfarm-order-" + UUID.randomUUID(), new MemoryPersistence());
        }
        if (!client.isConnected()) {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setConnectionTimeout(3);
            options.setAutomaticReconnect(true);
            if (!username.isBlank()) {
                options.setUserName(username);
                options.setPassword(password.toCharArray());
            }
            client.connect(options);
        }
        // Signs the payload when smartfarm.security.mqtt.sign-enabled=true; otherwise returns it unchanged.
        MqttMessage message = new MqttMessage(mqttSecurity.sign(topic, payload));
        message.setQos(1);
        message.setRetained(false);
        client.publish(topic, message);
    }

    @Override
    public synchronized void close() {
        if (client != null) {
            try {
                if (client.isConnected()) client.disconnect();
                client.close();
            } catch (MqttException ignored) {
            }
            client = null;
        }
    }
}
