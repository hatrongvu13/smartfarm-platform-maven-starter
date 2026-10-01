package com.htv.smartfarm.identity.messaging.command;

import java.time.Duration;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartfarm.identity.mqtt.commands")
public record IdentityMqttCommandProperties(
        boolean enabled,
        String topicFilter,
        int qos,
        int maximumPayloadBytes,
        Set<String> allowedTypes,
        Duration retention,
        int dispatchBatchSize,
        int maximumAttempts,
        Duration initialRetry,
        Duration maximumRetry,
        Duration processingTimeout
) {
    public IdentityMqttCommandProperties {
        topicFilter = text(topicFilter, "smartfarm/+/identity/command/+/v1");
        qos = qos == 0 ? 1 : qos;
        maximumPayloadBytes = maximumPayloadBytes <= 0 ? 65536 : maximumPayloadBytes;
        allowedTypes = allowedTypes == null ? Set.of() : Set.copyOf(allowedTypes);
        retention = retention == null || retention.isZero() || retention.isNegative()
                ? Duration.ofDays(30)
                : retention;
        dispatchBatchSize = dispatchBatchSize <= 0 ? 25 : Math.min(dispatchBatchSize, 200);
        maximumAttempts = maximumAttempts <= 0 ? 5 : maximumAttempts;
        initialRetry = duration(initialRetry, Duration.ofSeconds(5));
        maximumRetry = duration(maximumRetry, Duration.ofMinutes(5));
        processingTimeout = duration(processingTimeout, Duration.ofMinutes(2));
        if (qos < 0 || qos > 2) throw new IllegalArgumentException("Command qos must be 0..2");
        if (maximumPayloadBytes > 1048576) {
            throw new IllegalArgumentException("Command maximum-payload-bytes must not exceed 1048576");
        }
    }

    private static Duration duration(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
