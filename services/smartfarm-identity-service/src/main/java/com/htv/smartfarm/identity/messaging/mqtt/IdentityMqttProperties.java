package com.htv.smartfarm.identity.messaging.mqtt;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartfarm.identity.mqtt")
public record IdentityMqttProperties(
        boolean enabled,
        String brokerUri,
        String clientId,
        String username,
        String password,
        int qos,
        boolean cleanSession,
        Duration connectionTimeout,
        Duration keepAlive,
        Duration initialRetry,
        Duration maximumFastRetry,
        int degradedAfterAttempts,
        Duration degradedRetry,
        Duration probeAfter,
        Duration probeInterval
) {
    public IdentityMqttProperties {
        brokerUri = text(brokerUri, "tcp://localhost:1883");
        clientId = text(clientId, "smartfarm-identity-service");
        username = nullable(username);
        password = nullable(password);
        qos = qos == 0 ? 1 : qos;
        connectionTimeout = duration(connectionTimeout, Duration.ofSeconds(10));
        keepAlive = duration(keepAlive, Duration.ofSeconds(15));
        initialRetry = duration(initialRetry, Duration.ofSeconds(5));
        maximumFastRetry = duration(maximumFastRetry, Duration.ofSeconds(15));
        degradedAfterAttempts = degradedAfterAttempts <= 0 ? 30 : degradedAfterAttempts;
        degradedRetry = duration(degradedRetry, Duration.ofSeconds(60));
        probeAfter = duration(probeAfter, Duration.ofMinutes(10));
        probeInterval = duration(probeInterval, Duration.ofMinutes(5));
        if (qos < 0 || qos > 2) throw new IllegalArgumentException("MQTT qos must be 0..2");
        if (clientId.length() > 100) throw new IllegalArgumentException("MQTT client-id must not exceed 100 characters");
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String nullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Duration duration(Duration value, Duration fallback) {
        return value == null || value.isNegative() || value.isZero() ? fallback : value;
    }
}
