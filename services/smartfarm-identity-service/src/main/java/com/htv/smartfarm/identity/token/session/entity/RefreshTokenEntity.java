package com.htv.smartfarm.identity.token.session.entity;

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
@Table(name = "sf_refresh_token", indexes = {
        @Index(name = "ix_refresh_token_session", columnList = "session_id"),
        @Index(name = "ix_refresh_token_expiry", columnList = "expires_at")
})
public class RefreshTokenEntity {
    @Id @Column(name = "id", length = 36) private String id;
    @Column(name = "session_id", nullable = false, length = 36) private String sessionId;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(name = "issued_at", nullable = false) private Instant issuedAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "consumed_at") private Instant consumedAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "replaced_by_token_id", length = 36) private String replacedByTokenId;
    @Version @Column(name = "row_version", nullable = false) private long rowVersion;

    protected RefreshTokenEntity() { }
    public RefreshTokenEntity(String id,String sessionId,String tokenHash,Instant issuedAt,Instant expiresAt){
        this.id=req(id,"id");this.sessionId=req(sessionId,"sessionId");this.tokenHash=req(tokenHash,"tokenHash");
        this.issuedAt=Objects.requireNonNull(issuedAt);this.expiresAt=Objects.requireNonNull(expiresAt);
    }
    public boolean usable(Instant now){return consumedAt==null&&revokedAt==null&&expiresAt.isAfter(now);}
    public void consume(Instant now,String replacementId){if(!usable(now))throw new IllegalStateException("Refresh token is not usable");consumedAt=now;replacedByTokenId=req(replacementId,"replacementId");}
    public void revoke(Instant now){if(revokedAt==null)revokedAt=now;}
    private static String req(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" must not be blank");return v.trim();}
}
