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
        return health
                .withDetail("state", snapshot.state().name())
                .withDetail("connected", snapshot.connected())
                .withDetail("consecutiveFailures", snapshot.consecutiveFailures())
                .withDetail("clientId", snapshot.clientId())
                .withDetail("startedAt", snapshot.startedAt())
                .withDetail("lastConnectedAt", snapshot.lastConnectedAt())
                .withDetail("lastDisconnectedAt", snapshot.lastDisconnectedAt())
                .withDetail("lastErrorCode", snapshot.lastErrorCode())
                .build();
    }
}
