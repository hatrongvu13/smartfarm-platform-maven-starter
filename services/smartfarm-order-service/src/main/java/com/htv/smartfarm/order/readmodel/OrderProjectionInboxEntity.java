package com.htv.smartfarm.order.readmodel;

import java.time.Instant;
import jakarta.persistence.*;

@Entity
@Table(name = "ord_projection_inbox", indexes = {
        @Index(name = "ix_ord_projection_gap", columnList = "tenant_id,aggregate_id,aggregate_version,status"),
        @Index(name = "ix_ord_projection_status", columnList = "status,received_at")
})
public class OrderProjectionInboxEntity {
    @Id @Column(name = "event_id", length = 100) private String eventId;
    @Column(name = "tenant_id", nullable = false, length = 100) private String tenantId;
    @Column(name = "aggregate_id", nullable = false, length = 36) private String aggregateId;
    @Column(name = "aggregate_version", nullable = false) private long aggregateVersion;
    @Lob @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARBINARY) @Column(name = "payload", nullable = false) private byte[] payload;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 20)
    private OrderProjectionInboxStatus status;
    @Column(name = "received_at", nullable = false) private Instant receivedAt;
    @Column(name = "processed_at") private Instant processedAt;
    @Column(name = "last_error_code", length = 120) private String lastErrorCode;
    @Version @Column(name = "row_version", nullable = false) private long rowVersion;
    protected OrderProjectionInboxEntity() { }
    public OrderProjectionInboxEntity(String eventId, String tenantId, String aggregateId,
            long aggregateVersion, byte[] payload, Instant now) {
        this.eventId = required(eventId, "eventId"); this.tenantId = required(tenantId, "tenantId");
        this.aggregateId = required(aggregateId, "aggregateId");
        if (aggregateVersion < 1) throw new IllegalArgumentException("aggregateVersion must be positive");
        this.aggregateVersion = aggregateVersion;
        if (payload == null || payload.length == 0) throw new IllegalArgumentException("payload must not be empty");
        this.payload = payload.clone(); this.status = OrderProjectionInboxStatus.WAITING_GAP;
        this.receivedAt = java.util.Objects.requireNonNull(now);
    }
    public String getEventId() { return eventId; }
    public String getTenantId() { return tenantId; }
    public String getAggregateId() { return aggregateId; }
    public long getAggregateVersion() { return aggregateVersion; }
    public byte[] getPayload() { return payload.clone(); }
    public OrderProjectionInboxStatus getStatus() { return status; }
    public Instant getReceivedAt() { return receivedAt; }
    public void applied(Instant now) { status = OrderProjectionInboxStatus.APPLIED; processedAt = now; lastErrorCode = null; }
    public void ignored(Instant now) { status = OrderProjectionInboxStatus.IGNORED; processedAt = now; lastErrorCode = null; }
    public void dead(String code, Instant now) { status = OrderProjectionInboxStatus.DEAD; processedAt = now; lastErrorCode = limited(code, 120); }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
    private static String limited(String value, int maximum) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        return normalized.length() <= maximum ? normalized : normalized.substring(0, maximum);
    }
}
