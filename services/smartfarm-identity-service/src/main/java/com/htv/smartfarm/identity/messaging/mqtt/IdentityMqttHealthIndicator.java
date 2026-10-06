package com.htv.smartfarm.identity.messaging.mqtt;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component("identityMqtt")
@ConditionalOnProperty(
        prefix = "smartfarm.identity.mqtt",
        name = "enabled",
        havingValue = "true"
)
public class IdentityMqttHealthIndicator implements HealthIndicator {

    private final IdentityMqttConnectionManager manager;

    public IdentityMqttHealthIndicator(IdentityMqttConnectionManager manager) {
        this.manager = manager;
    }

    @Override
    public Health health() {
        var snapshot = manager.snapshot();
        Health.Builder health = snapshot.connected()
                ? Health.up()
                : Health.status(snapshot.state().name());
        health
                .withDetail("state", snapshot.state().name())
                .withDetail("connected", snapshot.connected())
                .withDetail("consecutiveFailures", snapshot.consecutiveFailures());
        // Optional fields are null until the client has connected / failed / errored.
        // Health.Builder.withDetail rejects null values, so only add them when present.
        addIfPresent(health, "clientId", snapshot.clientId());
        addIfPresent(health, "startedAt", snapshot.startedAt());
        addIfPresent(health, "lastConnectedAt", snapshot.lastConnectedAt());
        addIfPresent(health, "lastDisconnectedAt", snapshot.lastDisconnectedAt());
        addIfPresent(health, "lastErrorCode", snapshot.lastErrorCode());
        return health.build();
    }

    private static void addIfPresent(Health.Builder health, String key, Object value) {
        if (value != null) {
            health.withDetail(key, value);
        }
    }
}
