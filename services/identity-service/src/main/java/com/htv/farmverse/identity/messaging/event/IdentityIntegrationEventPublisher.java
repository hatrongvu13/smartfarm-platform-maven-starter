package com.htv.farmverse.identity.messaging.event;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.google.protobuf.Timestamp;
import com.htv.farmverse.common.contract.IdentityEventContract;
import com.htv.farmverse.identity.messaging.outbox.IdentityEventRecorder;
import com.htv.farmverse.proto.events.v1.DomainEvent;
import com.htv.farmverse.proto.events.v1.EventMetadata;
import com.htv.farmverse.proto.events.v1.IdentityLifecycleEvent;

import org.springframework.stereotype.Service;

@Service
public class IdentityIntegrationEventPublisher {

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
                .setFarmId(IdentityEventContract.GLOBAL_FARM_ID)
                .setAggregateId(nullToEmpty(aggregateId))
                .setCorrelationId(nullToEmpty(correlationId))
                .setCausationId(nullToEmpty(causationId))
                .setProducer(IdentityEventContract.IDENTITY_PRODUCER)
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

    public String publishAuthorizationInvalidated(
            String tenantId,
            String subjectId,
            String actorId,
            String correlationId,
            long tenantVersion,
            long subjectVersion,
            String reasonCode
    ) {
        if (tenantVersion < 0 || subjectVersion < 0) {
            throw new IllegalArgumentException("negative authorization version");
        }
        String tenant = java.util.Objects.requireNonNull(tenantId).trim();
        String subject = subjectId == null ? "" : subjectId.trim();
        String reason = java.util.Objects.requireNonNull(reasonCode).trim();
        if (tenant.isEmpty() || reason.isEmpty()) {
            throw new IllegalArgumentException("tenantId and reasonCode are required");
        }
        String eventId = UUID.randomUUID().toString();
        Instant occurredAt = clock.instant();
        EventMetadata metadata = EventMetadata.newBuilder()
                .setEventId(eventId)
                .setTenantId(tenant)
                .setFarmId(IdentityEventContract.GLOBAL_FARM_ID)
                .setAggregateId(subject.isEmpty() ? tenant : subject)
                .setCorrelationId(nullToEmpty(correlationId))
                .setProducer(IdentityEventContract.IDENTITY_PRODUCER)
                .setOccurredAt(toTimestamp(occurredAt))
                .setAggregateVersion(subject.isEmpty() ? tenantVersion : subjectVersion)
                .build();
        var invalidation = com.htv.farmverse.proto.events.v1.AuthorizationInvalidated
                .newBuilder()
                .setTenantId(tenant)
                .setSubjectId(subject)
                .setTenantAuthorizationVersion(tenantVersion)
                .setSubjectAuthorizationVersion(subjectVersion)
                .setReasonCode(reason)
                .build();
        byte[] payload = DomainEvent.newBuilder()
                .setMetadata(metadata)
                .setAuthorizationInvalidated(invalidation)
                .build()
                .toByteArray();
        String topic = IdentityEventTopics.domain(
                tenant,
                IdentityEventContract.AUTHORIZATION_INVALIDATED_EVENT_NAME,
                1
        );
        recorder.recordWithId(
                eventId,
                tenant,
                actorId,
                correlationId,
                null,
                IdentityEventContract.AUTHORIZATION_INVALIDATED_EVENT_TYPE,
                1,
                subject.isEmpty()
                        ? IdentityEventContract.AUTHORIZATION_TENANT_AGGREGATE_TYPE
                        : IdentityEventContract.AUTHORIZATION_SUBJECT_AGGREGATE_TYPE,
                subject.isEmpty() ? tenant : subject,
                subject.isEmpty() ? tenantVersion : subjectVersion,
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
