package com.htv.smartfarm.identity.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.htv.smartfarm.identity.account.domain.UserAccountEntity;
import com.htv.smartfarm.identity.account.repository.UserAccountRepository;
import com.htv.smartfarm.identity.tenant.domain.TenantEntity;
import com.htv.smartfarm.identity.tenant.domain.TenantMembershipEntity;
import com.htv.smartfarm.identity.tenant.repository.TenantMembershipRepository;
import com.htv.smartfarm.identity.tenant.repository.TenantRepository;
import com.htv.smartfarm.identity.token.grant.GrantType;
import com.htv.smartfarm.identity.token.grant.RefreshSessionPort;
import com.htv.smartfarm.identity.token.grant.TokenContext;
import com.htv.smartfarm.identity.token.session.RefreshSessionAdministrationService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;

@EnabledIfSystemProperty(named = "it.postgres.enabled", matches = "true")
class RefreshSessionTenantIsolationIT extends AbstractIdentityPostgresIT {
    @Autowired private RefreshSessionPort refreshSessions;
    @Autowired private RefreshSessionAdministrationService administration;
    @Autowired private TenantRepository tenants;
    @Autowired private UserAccountRepository accounts;
    @Autowired private TenantMembershipRepository memberships;

    @Test
    void anotherTenantCannotListOrRevokeTheSession() {
        String userId = "user-session-" + Long.toString(System.nanoTime(), 36);
        String tenantA = seedTenantAndMembership(userId, "a");
        String tenantB = seedTenantAndMembership(userId, "b");
        var grant = refreshSessions.create(context(userId, tenantA));

        assertThat(administration.list(tenantB, userId)).isEmpty();
        assertThatThrownBy(() -> administration.revoke(
                tenantB, userId, grant.sessionId(), "CROSS_TENANT_ATTEMPT"
        )).isInstanceOf(IllegalArgumentException.class);
        assertThat(administration.list(tenantA, userId))
                .extracting(value -> value.sessionId())
                .containsExactly(grant.sessionId());
    }

    @Test
    void concurrentRefreshAllowsOneRotationAndRevokesTheFamilyOnReplay() throws Exception {
        String userId = "user-refresh-" + Long.toString(System.nanoTime(), 36);
        String tenantId = seedTenantAndMembership(userId, "refresh");
        var original = refreshSessions.create(context(userId, tenantId));
        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<Object> attempt = () -> {
            barrier.await();
            try {
                return refreshSessions.rotate(original.rawToken());
            } catch (SecurityException failure) {
                return failure;
            }
        };

        var executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> results = executor.invokeAll(List.of(attempt, attempt));
            List<Object> rotations = List.of(results.get(0).get(), results.get(1).get());
            var successful = rotations.stream()
                    .filter(RefreshSessionPort.RefreshGrant.class::isInstance)
                    .map(RefreshSessionPort.RefreshGrant.class::cast)
                    .toList();

            assertThat(successful).hasSize(1);
            assertThat(rotations.stream()
                    .filter(SecurityException.class::isInstance))
                    .hasSize(1);
            assertThatThrownBy(() -> refreshSessions.rotate(successful.get(0).rawToken()))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("refresh token reuse detected");
        } finally {
            executor.shutdownNow();
        }
    }

    private TokenContext context(String userId, String tenantId) {
        return new TokenContext(
                GrantType.USER_LOGIN, userId, tenantId, "smartfarm-gateway",
                null, null, Set.of("FARM:READ"), Set.of("USER"),
                0, 0, null, Instant.now(), List.of("pwd"), Duration.ofMinutes(5)
        );
    }

    private String seedTenantAndMembership(String userId, String marker) {
        String suffix = marker + Long.toString(System.nanoTime(), 36);
        String tenantId = "tenant-session-" + suffix;
        tenants.save(new TenantEntity(tenantId, "code-" + suffix, "Tenant " + suffix));
        UserAccountEntity account = accounts.findById(userId).orElseGet(() ->
                accounts.save(new UserAccountEntity(
                        userId, userId + "@it.test", "x".repeat(40)
                ))
        );
        var membership = new TenantMembershipEntity(
                UUID.randomUUID().toString(),
                tenants.findById(tenantId).orElseThrow(),
                account
        );
        membership.activate();
        memberships.save(membership);
        return tenantId;
    }
}
