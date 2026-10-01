package com.htv.smartfarm.identity.messaging.event;

import java.time.Instant;
import java.util.Map;

public record IdentityEventPayload(
        String eventId,
        String eventType,
        int schemaVersion,
        String sourceService,
        String tenantId,
        String actorId,
        String correlationId,
        String causationId,
        String aggregateType,
        String aggregateId,
        long aggregateVersion,
        Instant occurredAt,
        Map<String, Object> data
) {
    public IdentityEventPayload {
        data = data == null ? Map.of() : Map.copyOf(data);
    }
}
