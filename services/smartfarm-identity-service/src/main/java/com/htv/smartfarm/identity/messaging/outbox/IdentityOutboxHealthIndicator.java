package com.htv.smartfarm.identity.messaging.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("identityOutbox")
@ConditionalOnProperty(
        prefix = "smartfarm.identity.outbox",
        name = "enabled",
        havingValue = "true"
)
public class IdentityOutboxHealthIndicator implements HealthIndicator {

    private final IdentityOutboxRepository repository;
    private final Clock clock;

    public IdentityOutboxHealthIndicator(IdentityOutboxRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public Health health() {
        long pending = repository.countByStatus(IdentityOutboxStatus.NEW)
                + repository.countByStatus(IdentityOutboxStatus.FAILED)
                + repository.countByStatus(IdentityOutboxStatus.PUBLISHING);
        long dead = repository.countByStatus(IdentityOutboxStatus.DEAD);
        Instant oldest = repository.oldestPending(List.of(
                IdentityOutboxStatus.NEW,
                IdentityOutboxStatus.FAILED,
                IdentityOutboxStatus.PUBLISHING
        ));
        long oldestAgeSeconds = oldest == null
                ? 0
                : Math.max(0, Duration.between(oldest, clock.instant()).toSeconds());
        Health.Builder builder = dead > 0
                ? Health.status("DEGRADED")
                : Health.up();
        return builder
                .withDetail("pending", pending)
                .withDetail("dead", dead)
                .withDetail("oldestPendingAgeSeconds", oldestAgeSeconds)
                .build();
    }
}
