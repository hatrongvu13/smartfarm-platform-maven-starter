package com.htv.smartfarm.identity.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.htv.smartfarm.identity.account.domain.UserAccountEntity;
import com.htv.smartfarm.identity.account.repository.UserAccountRepository;
import com.htv.smartfarm.identity.tenant.domain.TenantEntity;
import com.htv.smartfarm.identity.tenant.repository.TenantRepository;
import com.htv.smartfarm.identity.token.session.RefreshSessionCleanupService;
import com.htv.smartfarm.identity.token.session.entity.RefreshSessionEntity;
import com.htv.smartfarm.identity.token.session.entity.RefreshTokenEntity;
import com.htv.smartfarm.identity.token.session.repository.RefreshSessionJpaRepository;

import com.htv.smartfarm.identity.token.session.repository.RefreshTokenJpaRepository;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

@EnabledIfSystemProperty(named = "it.postgres.enabled", matches = "true")
class RefreshSessionCleanupIT extends AbstractIdentityPostgresIT {
    @Autowired private RefreshSessionCleanupService cleanup;
    @Autowired private RefreshSessionJpaRepository sessions;
    @Autowired private RefreshTokenJpaRepository tokens;
    @Autowired private TenantRepository tenants;
    @Autowired private UserAccountRepository accounts;

    @Test
    void cleanupDeletesExpiredTokensBeforeSessions() {
        String suffix = Long.toString(System.nanoTime(), 36);
        String tenantId = "tenant-cleanup-" + suffix;
        String userId = "user-cleanup-" + suffix;
        tenants.save(new TenantEntity(tenantId, "code-" + suffix, "Tenant " + suffix));
        accounts.save(new UserAccountEntity(userId, userId + "@it.test", "x".repeat(40)));
        Instant expired = Instant.now().minus(Duration.ofDays(30));
        String sessionId = UUID.randomUUID().toString();
        sessions.save(new RefreshSessionEntity(
                sessionId, UUID.randomUUID().toString(), userId, tenantId,
                "smartfarm-gateway", expired.minusSeconds(60), expired
        ));
        tokens.save(new RefreshTokenEntity(
                UUID.randomUUID().toString(), sessionId, "b".repeat(64),
                expired.minusSeconds(60), expired
        ));
        ReflectionTestUtils.setField(cleanup, "retention", Duration.ZERO);

        cleanup.cleanupExpiredSessions();

        assertThat(tokens.findAll()).isEmpty();
        assertThat(sessions.findById(sessionId)).isEmpty();
    }
}
