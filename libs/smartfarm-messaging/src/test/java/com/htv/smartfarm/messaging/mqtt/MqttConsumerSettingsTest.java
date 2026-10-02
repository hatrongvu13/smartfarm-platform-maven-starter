package com.htv.smartfarm.messaging.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;

class MqttConsumerSettingsTest {

    @Test
    void durableSessionWithMemoryIsRejected() {
        assertThatThrownBy(() -> new MqttConsumerSettings(
                "tcp://localhost:1883", "cid", 1, false, true,
                PersistenceMode.MEMORY, null, Duration.ofSeconds(3), Duration.ofSeconds(30), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MEMORY");
    }

    @Test
    void fileModeRequiresDirectory() {
        assertThatThrownBy(() -> new MqttConsumerSettings(
                "tcp://localhost:1883", "cid", 1, false, true,
                PersistenceMode.FILE, null, Duration.ofSeconds(3), Duration.ofSeconds(30), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("persistenceDirectory");
    }

    @Test
    void validDurableFileSettings() {
        var s = new MqttConsumerSettings(
                "tcp://localhost:1883", "cid", 1, false, true,
                PersistenceMode.FILE, Path.of("/tmp/x"), Duration.ofSeconds(3), Duration.ofSeconds(30), true);
        assertThat(s.cleanSession()).isFalse();
        assertThat(s.persistenceMode()).isEqualTo(PersistenceMode.FILE);
    }

    @Test
    void ephemeralMemoryIsValid() {
        var s = new MqttConsumerSettings(
                "tcp://localhost:1883", "cid", 1, true, false,
                PersistenceMode.MEMORY, null, null, null, true);
        assertThat(s.cleanSession()).isTrue();
        assertThat(s.connectionTimeout()).isEqualTo(Duration.ofSeconds(3)); // default applied
    }

    @Test
    void qosOutOfRangeRejected() {
        assertThatThrownBy(() -> new MqttConsumerSettings(
                "tcp://localhost:1883", "cid", 5, true, false,
                PersistenceMode.MEMORY, null, null, null, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
