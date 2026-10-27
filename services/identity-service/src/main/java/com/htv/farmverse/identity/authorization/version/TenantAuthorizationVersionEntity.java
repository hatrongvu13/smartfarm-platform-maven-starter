package com.htv.farmverse.identity.authorization.version;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "sf_authorization_tenant_version")
public class TenantAuthorizationVersionEntity {
    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 36)
    private String tenantId;

    @Column(name = "version_value", nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected TenantAuthorizationVersionEntity() { }

    public TenantAuthorizationVersionEntity(String tenantId, Instant now) {
        this.tenantId = required(tenantId, "tenantId");
        this.updatedAt = java.util.Objects.requireNonNull(now, "now");
    }

    public long increment(Instant now) {
        version = Math.addExact(version, 1L);
        updatedAt = java.util.Objects.requireNonNull(now, "now");
        return version;
    }

    public String getTenantId() { return tenantId; }
    public long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
