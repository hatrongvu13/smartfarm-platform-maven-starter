package com.htv.smartfarm.identity.token;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "sf_refresh",
        indexes = {
                @Index(name = "ix_refresh_family", columnList = "family_id"),
                @Index(name = "ix_refresh_user", columnList = "user_id"),
                @Index(
                        name = "ix_refresh_user_tenant",
                        columnList = "user_id,tenant_id"
                )
        }
)
public class RefreshEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "audience", nullable = false, length = 100)
    private String audience;

    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private long expiresAt;

    @Column(nullable = false)
    private boolean revoked;

    protected RefreshEntity() {
    }

    public RefreshEntity(
            String id,
            String userId,
            String tenantId,
            String audience,
            String familyId,
            String tokenHash,
            long expiresAt,
            boolean revoked
    ) {
        this.id = id;
        this.userId = userId;
        this.tenantId = tenantId;
        this.audience = audience;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.revoked = revoked;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getTenantId() { return tenantId; }
    public String getAudience() { return audience; }
    public String getFamilyId() { return familyId; }
    public long getExpiresAt() { return expiresAt; }
    public boolean isRevoked() { return revoked; }
}
