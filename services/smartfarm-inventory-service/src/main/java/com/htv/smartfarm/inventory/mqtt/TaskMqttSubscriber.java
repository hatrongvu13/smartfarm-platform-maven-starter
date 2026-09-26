package com.htv.smartfarm.inventory.mqtt;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Single local consumer; stable client ID + durable MQTT session; inbox commit precedes broker ack.
 */
@Component
@ConditionalOnProperty(prefix = "smartfarm.inventory.mqtt", name = "enabled", havingValue = "true")
public class TaskMqttSubscriber implements ApplicationRunner, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(TaskMqttSubscriber.class);
    private final TaskInbox inbox;
    private final String url, clientId, persistencePath;
    private MqttClient client;

    public TaskMqttSubscriber(TaskInbox inbox, @Value("${smartfarm.inventory.mqtt.url:tcp://localhost:1883}") String url, @Value("${smartfarm.inventory.mqtt.client-id:smartfarm-inventory-dev}") String clientId, @Value("${smartfarm.inventory.mqtt.persistence-path:./.local/mqtt-inventory}") String persistencePath) {
        this.inbox = inbox;
        this.url = url;
        this.clientId = clientId;
        this.persistencePath = persistencePath;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        java.nio.file.Files.createDirectories(java.nio.file.Path.of(persistencePath));
        client = new MqttClient(url, clientId, new MqttDefaultFilePersistence(persistencePath));
        client.setManualAcks(true);
        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                log.warn("Inventory MQTT disconnected");
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                try {
                    boolean first = inbox.accept(topic, message.getPayload());
                    client.messageArrivedComplete(message.getId(), message.getQos());
                    log.info("Task event accepted={} topic={}", first, topic);
                } catch (Exception e) {
                    log.error("Task event failed validation/DB; MQTT ack withheld; inspect and repair; topic={} cause={}", topic, e.getClass().getSimpleName());
                    throw e;
                }
            }
        });
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setCleanSession(false);
        opts.setAutomaticReconnect(true);
        opts.setConnectionTimeout(3);
        client.connect(opts);
        client.subscribe("smartfarm/+/+/domain/task-changed/v1", 1);
    }

    @Override
    public void close() {
        if (client != null) try {
            if (client.isConnected()) client.disconnect();
            client.close();
        } catch (MqttException e) {
            log.warn("MQTT close failed");
        }
    }
}
