package com.htv.smartfarm.messaging.mqtt;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Typed MQTT consumer connection/session/persistence settings, shared by every durable consumer.
 * This is a plain value object (not a {@code @ConfigurationProperties} bean) so each service binds
 * it under its own prefix and passes it to {@link MqttClientFactory}; the invariants below are the
 * ones ISSUE-05 is about, enforced centrally in the compact constructor.
 *
 * <p>Invariants:
 * <ul>
 *   <li>FILE persistence requires a non-null {@code persistenceDirectory} and a stable
 *       {@code clientId} (a durable session with a random client id loses its queue every restart).</li>
 *   <li>The forbidden hybrid {@code cleanSession=false + MEMORY} is rejected - it claims durability
 *       the transport cannot provide.</li>
 * </ul>
 */
public record MqttConsumerSettings(
        String brokerUri,
        String clientId,
        int qos,
        boolean cleanSession,
        boolean manualAcks,
        PersistenceMode persistenceMode,
        Path persistenceDirectory,
        Duration connectionTimeout,
        Duration keepAlive,
        boolean automaticReconnect
) {
    public MqttConsumerSettings {
        if (brokerUri == null || brokerUri.isBlank()) {
            throw new IllegalArgumentException("brokerUri is required");
        }
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId is required");
        }
        if (qos < 0 || qos > 2) {
            throw new IllegalArgumentException("qos must be 0..2");
        }
        if (persistenceMode == null) {
            throw new IllegalArgumentException("persistenceMode is required");
        }
        if (!cleanSession && persistenceMode == PersistenceMode.MEMORY) {
            throw new IllegalArgumentException(
                    "durable session (cleanSession=false) must not use MEMORY persistence; use FILE");
        }
        if (persistenceMode == PersistenceMode.FILE && persistenceDirectory == null) {
            throw new IllegalArgumentException("FILE persistence requires persistenceDirectory");
        }
        connectionTimeout = connectionTimeout == null ? Duration.ofSeconds(3) : connectionTimeout;
        keepAlive = keepAlive == null ? Duration.ofSeconds(30) : keepAlive;
    }
}
