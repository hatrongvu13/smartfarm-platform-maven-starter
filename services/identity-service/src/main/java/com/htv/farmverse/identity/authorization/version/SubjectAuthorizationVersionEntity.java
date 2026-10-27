package com.htv.farmverse.identity.authorization.version;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(
        name = "sf_authorization_subject_version",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_sf_authorization_subject_version_lookup",
                columnNames = {"tenant_id", "subject_id"}
        )
)
public class SubjectAuthorizationVersionEntity {
    @Id
    @Column(name = "membership_id", nullable = false, updatable = false, length = 36)
    private String membershipId;

    @Column(name = "tenant_id", nullable = false, updatable = false, length = 36)
    private String tenantId;

    @Column(name = "subject_id", nullable = false, updatable = false, length = 36)
    private String subjectId;

    @Column(name = "version_value", nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected SubjectAuthorizationVersionEntity() { }

    public SubjectAuthorizationVersionEntity(
            String membershipId,
            String tenantId,
            String subjectId,
            Instant now
    ) {
        this.membershipId = required(membershipId, "membershipId");
        this.tenantId = required(tenantId, "tenantId");
        this.subjectId = required(subjectId, "subjectId");
        this.updatedAt = java.util.Objects.requireNonNull(now, "now");
    }

    public long increment(Instant now) {
        version = Math.addExact(version, 1L);
        updatedAt = java.util.Objects.requireNonNull(now, "now");
        return version;
    }

    public String getMembershipId() { return membershipId; }
    public String getTenantId() { return tenantId; }
    public String getSubjectId() { return subjectId; }
    public long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
