package com.htv.farmverse.identity.token.session;

import java.time.Clock;
import java.util.List;
import com.htv.farmverse.identity.token.session.repository.RefreshSessionJpaRepository;
import com.htv.farmverse.identity.token.session.repository.RefreshTokenJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshSessionAdministrationService {
    private final RefreshSessionJpaRepository sessions;
    private final RefreshTokenJpaRepository tokens;
    private final Clock clock;

    @Transactional(readOnly=true)
    public List<SessionView> list(String tenantId,String subjectId){
        return sessions.findAllByUserIdAndTenantIdOrderByCreatedAtDesc(subjectId,tenantId).stream()
                .map(s->new SessionView(s.getId(),s.getUserId(),s.getTenantId(),s.getAudience(),s.getCreatedAt(),s.getLastUsedAt(),s.getExpiresAt(),s.getRevokedAt(),s.getRevokeReason())).toList();
    }
    @Transactional
    public boolean revoke(String tenantId,String subjectId,String sessionId,String reason){
        var s=sessions.lockOwned(sessionId,subjectId,tenantId).orElseThrow(()->new IllegalArgumentException("Refresh session not found"));
        boolean changed=s.revoke(clock.instant(),reason); if(changed)tokens.revokeSessionTokens(sessionId,clock.instant()); return changed;
    }
    @Transactional
    public int revokeAll(String tenantId,String subjectId,String reason){
        int count=0; for(var s:sessions.findAllByUserIdAndTenantIdOrderByCreatedAtDesc(subjectId,tenantId)){
            if(s.revoke(clock.instant(),reason)){tokens.revokeSessionTokens(s.getId(),clock.instant());count++;}
        } return count;
    }

    /** Compatibility command for global account/credential changes. */
    @Transactional
    public void revokeUser(String userId) {
        revokeSessions(
                sessions.findAllByUserIdOrderByCreatedAtDesc(userId),
                "USER_SECURITY_CHANGED"
        );
    }

    /** Compatibility command for tenant membership/account changes. */
    @Transactional
    public void revokeUserInTenant(String userId, String tenantId) {
        revokeSessions(
                sessions.findAllByUserIdAndTenantIdOrderByCreatedAtDesc(
                        userId,
                        tenantId
                ),
                "TENANT_ACCESS_CHANGED"
        );
    }

    private int revokeSessions(
            java.util.List<com.htv.farmverse.identity.token.session.entity.RefreshSessionEntity> values,
            String reason
    ) {
        int count = 0;
        var now = clock.instant();
        for (var session : values) {
            if (session.revoke(now, reason)) {
                tokens.revokeSessionTokens(session.getId(), now);
                count++;
            }
        }
        return count;
    }
}
