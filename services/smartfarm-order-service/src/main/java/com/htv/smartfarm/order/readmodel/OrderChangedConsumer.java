package com.htv.smartfarm.order.readmodel;

import java.nio.file.Path;

import com.htv.smartfarm.messaging.mqtt.MqttClientFactory;
import com.htv.smartfarm.messaging.mqtt.MqttConsumerSettings;
import com.htv.smartfarm.messaging.mqtt.PersistenceMode;
import com.htv.smartfarm.security.mqtt.MqttSecurityVerifier;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Subscribes to {@code order-changed} integration events on MQTT and maintains the
 * {@link OrderView} read-model (CQRS projection). Idempotent, replay-safe: an event older than what
 * the projection already holds is ignored, so redelivery or out-of-order arrival cannot corrupt it.
 *
 * <p><b>Durability (ISSUE-05, Option A).</b> This is a durable consumer: {@code cleanSession=false}
 * + FILE persistence (shared {@link MqttClientFactory}) + stable client id + MANUAL ACK. A QoS1
 * message is acknowledged to the broker ONLY after {@code projector.apply} returns (its own
 * transaction committed) or the message is a verified-but-rejected permanent drop. A transient
 * failure throws out of {@code messageArrived} WITHOUT acking, so the broker redelivers and the
 * on-disk persistence survives a restart. Duplicate delivery is a no-op in the projector (version
 * guard), then acked. The version-gap recovery worker remains the backstop.
 *
 * <p>Replica guard: the client id + persistence directory must be unique per running instance; two
 * instances sharing one durable client id concurrently is unsupported (run a single active consumer
 * or partition). FILE directory not writable =&gt; fail fast (factory throws), never silent memory.
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
    private final int qos;
    private final Path persistenceDir;
    private final MqttSecurityVerifier mqttSecurity;
    private MqttClient client;

    public OrderChangedConsumer(OrderViewJpaRepository views,
                                OrderViewProjector projector,
                                @Value("${smartfarm.mqtt.url:tcp://localhost:1883}") String url,
                                @Value("${smartfarm.mqtt.username:}") String username,
                                @Value("${smartfarm.mqtt.password:}") String password,
                                @Value("${smartfarm.order.readmodel.topic-filter:smartfarm/+/+/domain/order-changed/+}") String topicFilter,
                                @Value("${smartfarm.order.readmodel.client-id:smartfarm-order-readmodel-client}") String clientId,
                                @Value("${smartfarm.order.readmodel.qos:1}") int qos,
                                @Value("${smartfarm.order.readmodel.persistence-dir:${java.io.tmpdir}/smartfarm/order-readmodel}") String persistenceDir,
                                MqttSecurityVerifier mqttSecurity) {
        this.views = views;
        this.projector = projector;
        this.url = url;
        this.username = username;
        this.password = password;
        this.topicFilter = topicFilter;
        this.clientId = clientId;
        this.qos = qos;
        this.persistenceDir = Path.of(persistenceDir);
        this.mqttSecurity = mqttSecurity;
    }

    @PostConstruct
    public void start() {
        // Durable session, file persistence, manual ack - built via the shared factory. The settings
        // record rejects the forbidden cleanSession=false+MEMORY hybrid and a non-writable FILE dir.
        MqttConsumerSettings settings = new MqttConsumerSettings(
                url, clientId, qos, /*cleanSession*/ false, /*manualAcks*/ true,
                PersistenceMode.FILE, persistenceDir,
                java.time.Duration.ofSeconds(3), java.time.Duration.ofSeconds(30), /*autoReconnect*/ true);
        try {
            client = MqttClientFactory.newClient(settings);
            client.setManualAcks(true);
            MqttConnectOptions opts = MqttClientFactory.newConnectOptions(settings);
            if (!username.isBlank()) {
                opts.setUserName(username);
                opts.setPassword(password.toCharArray());
            }
            client.setCallback(new MqttCallbackExtended() {
                @Override public void connectComplete(boolean reconnect, String serverURI) {
                    try {
                        client.subscribe(topicFilter, qos);
                        log.info("OrderChanged consumer subscribed to '{}' (reconnect={}, durable, manualAck)", topicFilter, reconnect);
                    } catch (MqttException e) {
                        log.warn("OrderChanged subscribe failed: {}", e.getMessage());
                    }
                }
                @Override public void connectionLost(Throwable cause) {
                    log.warn("OrderChanged consumer connection lost: {}", cause == null ? "?" : cause.getMessage());
                }
                @Override public void messageArrived(String topic, MqttMessage message) throws Exception {
                    MqttSecurityVerifier.Result verification = mqttSecurity.verify(topic, message.getPayload());
                    if (!verification.accepted()) {
                        // Permanent, verified-invalid: ACK + drop so it is NOT redelivered forever.
                        log.warn("OrderChanged consumer dropped (quarantine) on '{}': {}", topic, verification.reason());
                        ack(message);
                        return;
                    }
                    try {
                        projector.apply(verification.payload());
                    } catch (RuntimeException transientFailure) {
                        // Do NOT ack: let the broker redeliver (durable session + file persistence).
                        log.warn("OrderChanged projection deferred (no ack, will redeliver) on '{}': {}",
                                topic, transientFailure.getMessage());
                        throw transientFailure;
                    }
                    // Projector committed (or no-op on duplicate via version guard): safe to ack now.
                    ack(message);
                }
                @Override public void deliveryComplete(IMqttDeliveryToken token) { }
            });
            try {
                client.connect(opts);
            } catch (MqttException deferred) {
                log.warn("OrderChanged consumer connect deferred (will retry): {}", deferred.getMessage());
            }
        } catch (RuntimeException e) {
            // Factory fail-fast (e.g. FILE persistence dir not writable) or client build failure:
            // read-model updates paused; do NOT silently fall back to memory.
            log.warn("OrderChanged consumer init failed (read-model updates paused): {}", e.getMessage());
        }
    }

    private void ack(MqttMessage message) {
        try {
            client.messageArrivedComplete(message.getId(), message.getQos());
        } catch (MqttException e) {
            log.warn("OrderChanged manual ack failed (will redeliver): {}", e.getMessage());
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
