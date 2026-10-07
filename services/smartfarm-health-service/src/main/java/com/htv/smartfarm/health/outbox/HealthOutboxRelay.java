package com.htv.smartfarm.health.outbox;

import com.htv.smartfarm.proto.events.v1.DomainEvent;

import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Outbox relay for health domain events. Active in every profile (gated by
 * {@code smartfarm.health.outbox.enabled}); the DB state changes only after broker confirmation.
 */
@Component
@ConditionalOnProperty(prefix = "smartfarm.health.outbox", name = "enabled", havingValue = "true")
public class HealthOutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(HealthOutboxRelay.class);
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9_-]{1,100}");
    private final HealthOutboxRepository repository;
    private final HealthEventPublisher publisher;

    public HealthOutboxRelay(HealthOutboxRepository repository, HealthEventPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    public void publishPending() {
        for (HealthOutboxRepository.Pending row : repository.pending(50)) {
            try {
                String topic = topic(row);
                DomainEvent event = DomainEvent.parseFrom(row.payload());
                if (!event.hasObservationRecorded()
                        || !row.eventId().equals(event.getMetadata().getEventId())
                        || !row.tenantId().equals(event.getMetadata().getTenantId())
                        || !row.farmId().equals(event.getMetadata().getFarmId())
                        || !row.eventType().equals("observation-recorded.v1")) {
                    throw new IllegalArgumentException("Outbox row does not match event payload");
                }
                publisher.publish(topic, row.payload());
                if (repository.markPublished(row.eventId()) != 1) {
                    log.warn("Outbox publish acknowledged but status not updated, eventId={}", row.eventId());
                }
            } catch (Exception error) {
                // Do not log payload or exception message: it may contain sensitive observations.
                log.warn("Outbox deferred, eventId={}, reason={}", row.eventId(), error.getClass().getSimpleName());
                break;
            }
        }
    }

    @Scheduled(fixedDelayString = "${smartfarm.health.outbox.poll-ms:2000}")
    public void scheduledPublish() {
        publishPending();
    }

    static String topic(HealthOutboxRepository.Pending row) {
        if (row.tenantId() == null || row.farmId() == null
                || !SEGMENT.matcher(row.tenantId()).matches()
                || !SEGMENT.matcher(row.farmId()).matches()) {
            throw new IllegalArgumentException("Invalid topic segment");
        }
        return "smartfarm/" + row.tenantId() + "/" + row.farmId()
                + "/domain/observation-recorded/v1";
    }
}
