package com.htv.smartfarm.identity.messaging.outbox;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityEventRecorder {

    private final IdentityOutboxRepository repository;
    private final Clock clock;

    public IdentityEventRecorder(IdentityOutboxRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public String recordWithId(
            String eventId,
            String tenantId,
            String actorId,
            String correlationId,
            String causationId,
            String eventType,
            int schemaVersion,
            String aggregateType,
            String aggregateId,
            long aggregateVersion,
            String topic,
            byte[] payload,
            Instant occurredAt
    ) {
        repository.save(new IdentityOutboxEntity(
                eventId,
                tenantId,
                actorId,
                correlationId,
                causationId,
                eventType,
                schemaVersion,
                aggregateType,
                aggregateId,
                aggregateVersion,
                topic,
                payload,
                occurredAt
        ));
        return eventId;
    }

    @Transactional
    public String record(
            String tenantId,
            String actorId,
            String correlationId,
            String causationId,
            String eventType,
            int schemaVersion,
            String aggregateType,
            String aggregateId,
            long aggregateVersion,
            String topic,
            byte[] payload
    ) {
        Instant now = clock.instant();
        String eventId = UUID.randomUUID().toString();
        repository.save(new IdentityOutboxEntity(
                eventId,
                tenantId,
                actorId,
                correlationId,
                causationId,
                eventType,
                schemaVersion,
                aggregateType,
                aggregateId,
                aggregateVersion,
                topic,
                payload,
                now
        ));
        return eventId;
    }
}
