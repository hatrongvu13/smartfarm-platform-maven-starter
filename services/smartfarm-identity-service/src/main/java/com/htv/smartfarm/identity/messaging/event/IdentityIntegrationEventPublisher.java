package com.htv.smartfarm.identity.messaging.event;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.google.protobuf.Timestamp;
import com.htv.smartfarm.identity.messaging.outbox.IdentityEventRecorder;
import com.htv.smartfarm.proto.events.v1.DomainEvent;
import com.htv.smartfarm.proto.events.v1.EventMetadata;
import com.htv.smartfarm.proto.events.v1.IdentityLifecycleEvent;

import org.springframework.stereotype.Service;

/**
 * Publishes identity account/role/membership/mfa lifecycle events as protobuf {@link DomainEvent}
 * frames (payload {@link IdentityLifecycleEvent}) — the SAME wire format every other service uses,
 * so the gateway WS bridge ({@code DomainEvent.parseFrom}) accepts them. Previously these were
 * serialized as JSON, which the protobuf-only bridge silently dropped (EVT-01).
 */
@Service
public class IdentityIntegrationEventPublisher {

    private static final String SOURCE = "smartfarm-identity-service";

    private final IdentityEventRecorder recorder;
    private final Clock clock;

    public IdentityIntegrationEventPublisher(
            IdentityEventRecorder recorder,
            Clock clock
    ) {
        this.recorder = recorder;
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

        EventMetadata.Builder metadata = EventMetadata.newBuilder()
                .setEventId(eventId)
                .setTenantId(nullToEmpty(tenantId))
                // Identity is a tenant-global aggregate: no farm scope. The WS bridge keys on this.
                .setFarmId("_global")
                .setAggregateId(nullToEmpty(aggregateId))
                .setCorrelationId(nullToEmpty(correlationId))
                .setCausationId(nullToEmpty(causationId))
                .setProducer(SOURCE)
                .setOccurredAt(toTimestamp(occurredAt))
                .setAggregateVersion(aggregateVersion);

        IdentityLifecycleEvent.Builder lifecycle = IdentityLifecycleEvent.newBuilder()
                .setEventType(nullToEmpty(eventType))
                .setAggregateType(nullToEmpty(aggregateType))
                .setActorId(nullToEmpty(actorId));
        if (data != null) {
            data.forEach((k, v) -> {
                if (k != null) {
                    lifecycle.putData(k, v == null ? "" : String.valueOf(v));
                }
            });
        }

        byte[] payload = DomainEvent.newBuilder()
                .setMetadata(metadata)
                .setIdentityLifecycleEvent(lifecycle)
                .build()
                .toByteArray();

        String eventName = eventType.substring(eventType.lastIndexOf('.') + 1);
        String topic = IdentityEventTopics.domain(
                tenantId,
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

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
