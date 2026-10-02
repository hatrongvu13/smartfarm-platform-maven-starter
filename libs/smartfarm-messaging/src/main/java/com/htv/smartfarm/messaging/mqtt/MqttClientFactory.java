package com.htv.smartfarm.messaging.mqtt;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttClientPersistence;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;

/**
 * Builds a Paho {@link MqttClient} + {@link MqttConnectOptions} from {@link MqttConsumerSettings}.
 * This is the one place that touches Paho construction (Adapter), so connection/session/persistence
 * wiring is no longer copy-pasted across consumers. It does NOT subscribe, set callbacks, or know any
 * topic - the owning service does that (business stays at the owner).
 *
 * <p>FILE mode fails fast if the per-client directory is not writable, rather than silently falling
 * back to memory (which would quietly drop the durability guarantee).
 */
public final class MqttClientFactory {

    private MqttClientFactory() {
    }

    /** Create (but do not connect) a client with persistence per the settings. */
    public static MqttClient newClient(MqttConsumerSettings settings) {
        try {
            return new MqttClient(settings.brokerUri(), settings.clientId(), persistence(settings));
        } catch (MqttException e) {
            throw new IllegalStateException("failed to create MQTT client for id=" + settings.clientId(), e);
        }
    }

    /** Connect options derived from the settings. Credentials are applied by the caller to avoid logging them here. */
    public static MqttConnectOptions newConnectOptions(MqttConsumerSettings settings) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(settings.cleanSession());
        options.setConnectionTimeout((int) settings.connectionTimeout().toSeconds());
        options.setKeepAliveInterval((int) settings.keepAlive().toSeconds());
        options.setAutomaticReconnect(settings.automaticReconnect());
        return options;
    }

    private static MqttClientPersistence persistence(MqttConsumerSettings settings) {
        if (settings.persistenceMode() == PersistenceMode.MEMORY) {
            return new MemoryPersistence();
        }
        Path dir = settings.persistenceDirectory();
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new IllegalStateException("MQTT FILE persistence directory not creatable: " + dir, e);
        }
        if (!Files.isWritable(dir)) {
            throw new IllegalStateException("MQTT FILE persistence directory not writable: " + dir);
        }
        return new MqttDefaultFilePersistence(dir.toString());
    }
}
