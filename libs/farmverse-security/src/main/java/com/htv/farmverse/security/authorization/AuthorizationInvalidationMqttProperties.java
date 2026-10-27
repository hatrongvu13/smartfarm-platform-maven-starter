package com.htv.farmverse.security.authorization;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "farmverse.security.authorization-invalidation.mqtt")
public record AuthorizationInvalidationMqttProperties(
        boolean enabled,
        String brokerUrl,
        String topicFilter,
        String clientId,
        int qos,
        String username,
        String password
) {
    public AuthorizationInvalidationMqttProperties {
        brokerUrl = text(brokerUrl, "tcp://localhost:1883");
        topicFilter = text(topicFilter, "farmverse/+/_global/domain/authorization-invalidated/v1");
        clientId = text(clientId, defaultClientId());
        qos = qos < 0 || qos > 2 ? 1 : qos;
        username = username == null ? "" : username.trim();
        password = password == null ? "" : password;
    }
    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
    private static String defaultClientId() {
        String app = System.getProperty("spring.application.name", "farmverse-service");
        String instance = System.getenv().getOrDefault("HOSTNAME", "local");
        String value = app + "-authorization-watermark-" + instance;
        return value.length() <= 120 ? value : value.substring(0, 120);
    }
}
