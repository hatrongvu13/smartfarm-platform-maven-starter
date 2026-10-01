package com.htv.smartfarm.identity.messaging.command;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("identityMqttInbox")
@ConditionalOnProperty(
        prefix = "smartfarm.identity.mqtt.commands",
        name = "enabled",
        havingValue = "true"
)
public class IdentityMqttInboxHealthIndicator implements HealthIndicator {

    private final IdentityMqttInboxRepository inbox;

    public IdentityMqttInboxHealthIndicator(IdentityMqttInboxRepository inbox) {
        this.inbox = inbox;
    }

    @Override
    public Health health() {
        long received = inbox.countByStatus(IdentityMqttCommandStatus.RECEIVED);
        long rejected = inbox.countByStatus(IdentityMqttCommandStatus.REJECTED);
        long failed = inbox.countByStatus(IdentityMqttCommandStatus.FAILED);
        Health.Builder builder = failed > 0 ? Health.status("DEGRADED") : Health.up();
        return builder
                .withDetail("received", received)
                .withDetail("rejected", rejected)
                .withDetail("failed", failed)
                .build();
    }
}
