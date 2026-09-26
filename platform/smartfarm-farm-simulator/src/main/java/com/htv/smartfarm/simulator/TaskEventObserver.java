package com.htv.smartfarm.simulator;

import com.htv.smartfarm.proto.events.v1.DomainEvent;

import java.util.UUID;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Local diagnostic subscriber; not a durable inbox or business consumer.
 */
@Component
public class TaskEventObserver implements ApplicationRunner, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(TaskEventObserver.class);
    private final String url;
    private MqttClient client;

    public TaskEventObserver(@Value("${smartfarm.mqtt.url:tcp://localhost:1883}") String url) {
        this.url = url;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        client = new MqttClient(url, "smartfarm-simulator-observer-" + UUID.randomUUID(), new MemoryPersistence());
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setCleanSession(true);
        opts.setConnectionTimeout(3);
        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                log.warn("MQTT observer disconnected");
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                try {
                    DomainEvent event = DomainEvent.parseFrom(message.getPayload());
                    if (event.hasTaskChanged())
                        log.info("Observed task event: eventId={} taskId={} topic={}", event.getMetadata().getEventId(), event.getTaskChanged().getTask().getTaskId(), topic);
                } catch (Exception bad) {
                    log.warn("Ignored malformed event on topic={}", topic);
                }
            }
        });
        client.connect(opts);
        client.subscribe("smartfarm/+/+/domain/task-changed/v1", 1);
        log.info("Simulator subscribed to task-changed events");
    }

    @Override
    public void close() {
        if (client != null) try {
            if (client.isConnected()) client.disconnect();
            client.close();
        } catch (MqttException ignored) {
        }
    }
}
