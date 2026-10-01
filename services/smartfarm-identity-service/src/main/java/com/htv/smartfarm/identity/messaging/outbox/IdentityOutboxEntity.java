package com.htv.smartfarm.identity.messaging.outbox;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(
        name = "sf_identity_outbox",
        indexes = {
                @Index(name = "ix_identity_outbox_delivery", columnList = "status,next_attempt_at,occurred_at"),
                @Index(name = "ix_identity_outbox_aggregate", columnList = "tenant_id,aggregate_type,aggregate_id,aggregate_version")
        }
)
public class IdentityOutboxEntity {

    @Id
    @Column(name = "event_id", length = 36)
    private String eventId;

    @Column(name = "tenant_id", length = 100)
    private String tenantId;

    @Column(name = "actor_id", length = 100)
    private String actorId;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "causation_id", length = 128)
    private String causationId;

    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(name = "aggregate_type", nullable = false, length = 80)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "aggregate_version", nullable = false)
    private long aggregateVersion;

    @Column(name = "topic", nullable = false, length = 300)
    private String topic;

    @Lob
    @Column(name = "payload", nullable = false)
    private byte[] payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IdentityOutboxStatus status;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error_code", length = 120)
    private String lastErrorCode;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected IdentityOutboxEntity() {
    }

    public IdentityOutboxEntity(
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
        this.eventId = required(eventId, "eventId");
        this.tenantId = nullable(tenantId);
        this.actorId = nullable(actorId);
        this.correlationId = nullable(correlationId);
        this.causationId = nullable(causationId);
        this.eventType = required(eventType, "eventType");
        this.schemaVersion = schemaVersion;
        this.aggregateType = required(aggregateType, "aggregateType");
        this.aggregateId = required(aggregateId, "aggregateId");
        this.aggregateVersion = aggregateVersion;
        this.topic = required(topic, "topic");
        this.payload = payload == null ? null : payload.clone();
        if (this.payload == null || this.payload.length == 0) {
            throw new IllegalArgumentException("payload must not be empty");
        }
        this.occurredAt = occurredAt;
        this.nextAttemptAt = occurredAt;
        this.status = IdentityOutboxStatus.NEW;
    }

    public String getEventId() { return eventId; }
    public String getTenantId() { return tenantId; }
    public String getActorId() { return actorId; }
    public String getCorrelationId() { return correlationId; }
    public String getCausationId() { return causationId; }
    public String getEventType() { return eventType; }
    public int getSchemaVersion() { return schemaVersion; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public long getAggregateVersion() { return aggregateVersion; }
    public String getTopic() { return topic; }
    public byte[] getPayload() { return payload.clone(); }
    public IdentityOutboxStatus getStatus() { return status; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getClaimedAt() { return claimedAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getLastErrorCode() { return lastErrorCode; }

    public void claim(Instant now) {
        if (!status.retryable()) throw new IllegalStateException("Outbox event is not retryable");
        status = IdentityOutboxStatus.PUBLISHING;
        claimedAt = now;
    }

    public void published(Instant now) {
        status = IdentityOutboxStatus.PUBLISHED;
        publishedAt = now;
        claimedAt = null;
        lastErrorCode = null;
    }

    public void failed(Instant nextAttempt, String errorCode, int maximumAttempts) {
        attemptCount++;
        status = attemptCount >= maximumAttempts
                ? IdentityOutboxStatus.DEAD
                : IdentityOutboxStatus.FAILED;
        nextAttemptAt = nextAttempt;
        claimedAt = null;
        lastErrorCode = nullable(errorCode);
    }

    public void recover(Instant now) {
        if (status == IdentityOutboxStatus.PUBLISHING) {
            status = IdentityOutboxStatus.FAILED;
            nextAttemptAt = now;
            claimedAt = null;
            lastErrorCode = "STALE_CLAIM";
        }
    }

    public void retryDead(Instant now) {
        if (status != IdentityOutboxStatus.DEAD) throw new IllegalStateException("Event is not dead-lettered");
        status = IdentityOutboxStatus.FAILED;
        nextAttemptAt = now;
        attemptCount = 0;
        claimedAt = null;
        lastErrorCode = null;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static String nullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
