package com.htv.smartfarm.identity.token.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import com.htv.smartfarm.identity.config.IdentitySettings;
import com.htv.smartfarm.identity.token.grant.RefreshSessionDescriptor;
import com.htv.smartfarm.identity.token.grant.RefreshSessionPort;
import com.htv.smartfarm.identity.token.grant.TokenContext;
import com.htv.smartfarm.identity.token.session.entity.RefreshSessionEntity;
import com.htv.smartfarm.identity.token.session.entity.RefreshTokenEntity;
import com.htv.smartfarm.identity.token.session.repository.RefreshSessionJpaRepository;
import com.htv.smartfarm.identity.token.session.repository.RefreshTokenJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class JpaRefreshSessionAdapter implements RefreshSessionPort {
    private final RefreshSessionJpaRepository sessions; private final RefreshTokenJpaRepository tokens;
    private final IdentitySettings settings; private final Clock clock; private final SecureRandom random=new SecureRandom();
    @Override @Transactional
    public RefreshGrant create(TokenContext context){
        var now=clock.instant();String sid=UUID.randomUUID().toString(),family=UUID.randomUUID().toString();
        sessions.save(new RefreshSessionEntity(sid,family,context.subjectId(),context.tenantId(),context.audience(),now,now.plus(settings.refreshTtl())));
        return issue(sid,family,now);
    }
    @Override @Transactional(readOnly = true)
    public RefreshSessionDescriptor resolve(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("refresh token must not be blank");
        }
        var token = tokens.lockByTokenHash(hash(raw))
                .orElseThrow(() -> new SecurityException("invalid refresh token"));
        var session = sessions.findById(token.getSessionId())
                .orElseThrow(() -> new SecurityException("refresh session not found"));
        return new RefreshSessionDescriptor(
                session.getId(),
                session.getFamilyId(),
                session.getUserId(),
                session.getTenantId(),
                session.getAudience()
        );
    }

    @Override @Transactional
    public RefreshGrant rotate(String raw){
        if(raw==null||raw.isBlank())throw new IllegalArgumentException("refresh token must not be blank");
        var now=clock.instant();var old=tokens.lockByTokenHash(hash(raw)).orElseThrow(()->new SecurityException("invalid refresh token"));
        var session=sessions.findById(old.getSessionId()).orElseThrow(()->new SecurityException("refresh session not found"));
        if(!session.active(now)||!old.usable(now)){session.revoke(now,"REFRESH_TOKEN_REUSE");tokens.revokeSessionTokens(session.getId(),now);throw new SecurityException("refresh token reuse detected");}
        String replacementId=UUID.randomUUID().toString();String next=raw();
        tokens.save(new RefreshTokenEntity(replacementId,session.getId(),hash(next),now,session.getExpiresAt()));
        old.consume(now,replacementId);session.touch(now);return new RefreshGrant(next,session.getId(),session.getFamilyId());
    }
    @Override @Transactional
    public void revokeFamily(String familyId,String reason){sessions.lockByFamilyId(familyId).ifPresent(s->{var now=clock.instant();s.revoke(now,reason);tokens.revokeSessionTokens(s.getId(),now);});}
    @Override @Transactional
    public void revokeByRawToken(String raw,String reason){
        if(raw==null||raw.isBlank())return;
        tokens.lockByTokenHash(hash(raw)).ifPresent(t->sessions.findById(t.getSessionId()).ifPresent(s->{var now=clock.instant();s.revoke(now,reason);tokens.revokeSessionTokens(s.getId(),now);}));
    }
    private RefreshGrant issue(String sid,String family,java.time.Instant now){String id=UUID.randomUUID().toString(),raw=raw();tokens.save(new RefreshTokenEntity(id,sid,hash(raw),now,now.plus(settings.refreshTtl())));return new RefreshGrant(raw,sid,family);}
    private String raw(){byte[] b=new byte[48];random.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    private String hash(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
