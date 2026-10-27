package com.htv.farmverse.identity.token.session.entity;

import java.time.Instant;
import java.util.Objects;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

@Getter
@Entity
@Table(name = "sf_refresh_session", indexes = {
        @Index(name = "ix_refresh_session_user_tenant", columnList = "user_id,tenant_id"),
        @Index(name = "ix_refresh_session_expiry", columnList = "expires_at")
})
public class RefreshSessionEntity {
    @Id @Column(name = "id", length = 36) private String id;
    @Column(name = "family_id", nullable = false, unique = true, length = 36) private String familyId;
    @Column(name = "user_id", nullable = false, length = 36) private String userId;
    @Column(name = "tenant_id", nullable = false, length = 36) private String tenantId;
    @Column(name = "audience", nullable = false, length = 100) private String audience;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "last_used_at") private Instant lastUsedAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "revoke_reason", length = 120) private String revokeReason;
    @Version @Column(name = "row_version", nullable = false) private long rowVersion;

    protected RefreshSessionEntity() { }
    public RefreshSessionEntity(String id, String familyId, String userId, String tenantId,
            String audience, Instant createdAt, Instant expiresAt) {
        this.id=req(id,"id"); this.familyId=req(familyId,"familyId"); this.userId=req(userId,"userId");
        this.tenantId=req(tenantId,"tenantId"); this.audience=req(audience,"audience");
        this.createdAt=Objects.requireNonNull(createdAt); this.expiresAt=Objects.requireNonNull(expiresAt);
    }
    public boolean active(Instant now) { return revokedAt == null && expiresAt.isAfter(now); }
    public void touch(Instant now) { if (!active(now)) throw new IllegalStateException("Refresh session is not active"); lastUsedAt=now; }
    public boolean revoke(Instant now, String reason) {
        if (revokedAt != null) return false;
        revokedAt=Objects.requireNonNull(now); revokeReason=nullable(reason); return true;
    }
    private static String req(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" must not be blank");return v.trim();}
    private static String nullable(String v){return v==null||v.isBlank()?null:v.trim();}
}
