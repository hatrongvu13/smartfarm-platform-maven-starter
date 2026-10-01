package com.htv.smartfarm.identity.messaging.event;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.htv.smartfarm.identity.messaging.outbox.IdentityEventRecorder;

import org.springframework.stereotype.Service;

@Service
public class IdentityIntegrationEventPublisher {

    private static final String SOURCE = "smartfarm-identity-service";

    private final IdentityEventRecorder recorder;
    private final ObjectMapper objectMapper;
    private final Clock clock;


    public IdentityIntegrationEventPublisher(
            IdentityEventRecorder recorder,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.recorder = recorder;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public String publish(
            String tenantId,
            String actorId,
            String correlationId,
            String causationId,
            String eventType,
            String aggregateType,
            String aggregateId,
            long aggregateVersion,
            Map<String, Object> data
    ) {
        int schemaVersion = 1;
        String eventId = UUID.randomUUID().toString();
        Instant occurredAt = clock.instant();
        IdentityEventPayload envelope = new IdentityEventPayload(
                eventId,
                eventType,
                schemaVersion,
                SOURCE,
                tenantId,
                actorId,
                correlationId,
                causationId,
                aggregateType,
                aggregateId,
                aggregateVersion,
                occurredAt,
                data
        );
        byte[] payload;
        try {
            payload = objectMapper.writeValueAsString(envelope).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize identity integration event", exception);
        }
        String eventName = eventType.substring(eventType.lastIndexOf('.') + 1);
        String topic = IdentityEventTopics.domain(
                tenantId,
                aggregateType,
                eventName,
                schemaVersion
        );
        recorder.recordWithId(
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
        );
        return eventId;
    }
}
