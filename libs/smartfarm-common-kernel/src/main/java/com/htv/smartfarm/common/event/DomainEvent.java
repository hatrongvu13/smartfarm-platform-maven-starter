package com.htv.smartfarm.common.event;

import java.time.Instant;
import java.util.UUID;

public record DomainEvent<T>(UUID eventId, String eventType, String aggregateId, Instant occurredAt,
                             String correlationId, int schemaVersion, T payload) {
    public static <T> DomainEvent<T> of(String type, String aggregateId, String correlationId, T payload) {
        return new DomainEvent<>(UUID.randomUUID(), type, aggregateId, Instant.now(), correlationId, 1, payload);
    }
}
