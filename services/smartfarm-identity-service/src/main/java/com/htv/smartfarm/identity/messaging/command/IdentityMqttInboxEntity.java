package com.htv.smartfarm.identity.messaging.command;

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
        name = "sf_identity_mqtt_inbox",
        indexes = {
                @Index(name = "ix_identity_inbox_status_received", columnList = "status,received_at"),
                @Index(name = "ix_identity_inbox_tenant_type", columnList = "tenant_id,command_type,received_at")
        }
)
public class IdentityMqttInboxEntity {

    @Id
    @Column(name = "command_id", length = 100)
    private String commandId;

    @Column(name = "tenant_id", length = 100)
    private String tenantId;

    @Column(name = "actor_id", length = 100)
    private String actorId;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "command_type", length = 120)
    private String commandType;

    @Column(name = "schema_version")
    private Integer schemaVersion;

    @Column(name = "topic", nullable = false, length = 300)
    private String topic;

    @Lob
    @Column(name = "payload")
    private byte[] payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IdentityMqttCommandStatus status;

    @Column(name = "rejection_code", length = 120)
    private String rejectionCode;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error_code", length = 120)
    private String lastErrorCode;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected IdentityMqttInboxEntity() {
    }

    private IdentityMqttInboxEntity(
            String commandId,
            String tenantId,
            String actorId,
            String correlationId,
            String commandType,
            Integer schemaVersion,
            String topic,
            byte[] payload,
            IdentityMqttCommandStatus status,
            String rejectionCode,
            Instant receivedAt,
            Instant issuedAt
    ) {
        this.commandId = required(commandId, "commandId");
        this.tenantId = nullable(tenantId);
        this.actorId = nullable(actorId);
        this.correlationId = nullable(correlationId);
        this.commandType = nullable(commandType);
        this.schemaVersion = schemaVersion;
        this.topic = required(topic, "topic");
        this.payload = payload == null ? null : payload.clone();
        this.status = status;
        this.rejectionCode = nullable(rejectionCode);
        this.receivedAt = receivedAt;
        this.issuedAt = issuedAt;
        this.nextAttemptAt = receivedAt;
    }

    public static IdentityMqttInboxEntity received(
            IdentityMqttCommandEnvelope envelope,
            String topic,
            byte[] payload,
            Instant now
    ) {
        return new IdentityMqttInboxEntity(
                envelope.commandId(), envelope.tenantId(), envelope.actorId(),
                envelope.correlationId(), envelope.commandType(), envelope.schemaVersion(),
                topic, payload, IdentityMqttCommandStatus.RECEIVED, null, now, envelope.issuedAt()
        );
    }

    public static IdentityMqttInboxEntity rejected(
            String commandId,
            String topic,
            byte[] payload,
            String rejectionCode,
            Instant now
    ) {
        return new IdentityMqttInboxEntity(
                commandId, null, null, null, null, null,
                topic, payload, IdentityMqttCommandStatus.REJECTED, rejectionCode, now, null
        );
    }

    public String getCommandId() { return commandId; }
    public String getTenantId() { return tenantId; }
    public String getActorId() { return actorId; }
    public String getCorrelationId() { return correlationId; }
    public String getCommandType() { return commandType; }
    public byte[] getPayload() { return payload == null ? null : payload.clone(); }
    public IdentityMqttCommandStatus getStatus() { return status; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getClaimedAt() { return claimedAt; }
    public int getAttemptCount() { return attemptCount; }

    public void claim(Instant now) {
        if (status != IdentityMqttCommandStatus.RECEIVED
                && status != IdentityMqttCommandStatus.FAILED) {
            throw new IllegalStateException("Command is not dispatchable");
        }
        status = IdentityMqttCommandStatus.PROCESSING;
        claimedAt = now;
    }

    public void processed(Instant now) {
        status = IdentityMqttCommandStatus.PROCESSED;
        processedAt = now;
        claimedAt = null;
        lastErrorCode = null;
    }

    public void failed(Instant nextAttempt, String errorCode, int maximumAttempts) {
        attemptCount++;
        status = IdentityMqttCommandStatus.FAILED;
        this.nextAttemptAt = nextAttempt;
        claimedAt = null;
        lastErrorCode = nullable(errorCode);
        if (attemptCount >= maximumAttempts) this.nextAttemptAt = Instant.MAX;
    }

    public void recover(Instant now) {
        if (status == IdentityMqttCommandStatus.PROCESSING) {
            status = IdentityMqttCommandStatus.FAILED;
            nextAttemptAt = now;
            claimedAt = null;
            lastErrorCode = "STALE_CLAIM";
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static String nullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
