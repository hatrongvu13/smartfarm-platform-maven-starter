package com.htv.smartfarm.common.event;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DomainEvent<T>(
        UUID eventId,
        String eventType,
        String aggregateId,
        Instant occurredAt,
        EventMetadata metadata,
        int schemaVersion,
        T payload
) {
    public static final int INITIAL_SCHEMA_VERSION = 1;

    public DomainEvent {
        eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        eventType = requireText(eventType, "eventType");
        aggregateId = requireText(aggregateId, "aggregateId");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        metadata = Objects.requireNonNull(metadata, "metadata must not be null");
        payload = Objects.requireNonNull(payload, "payload must not be null");
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be greater than zero");
        }
    }

    public static <T> DomainEvent<T> create(String eventType, String aggregateId,
                                            EventMetadata metadata, T payload) {
        return create(eventType, aggregateId, metadata, INITIAL_SCHEMA_VERSION, payload, Clock.systemUTC());
    }

    public static <T> DomainEvent<T> create(String eventType, String aggregateId,
                                            EventMetadata metadata, int schemaVersion, T payload) {
        return create(eventType, aggregateId, metadata, schemaVersion, payload, Clock.systemUTC());
    }

    static <T> DomainEvent<T> create(String eventType, String aggregateId,
                                     EventMetadata metadata, int schemaVersion,
                                     T payload, Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        return new DomainEvent<>(UUID.randomUUID(), eventType, aggregateId,
                clock.instant(), metadata, schemaVersion, payload);
    }

    @Deprecated(forRemoval = true)
    public static <T> DomainEvent<T> of(String eventType, String aggregateId,
                                        String correlationId, T payload) {
        return create(eventType, aggregateId,
                new EventMetadata(correlationId, null, null, null, "unknown"), payload);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
