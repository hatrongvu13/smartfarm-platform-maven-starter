package com.htv.smartfarm.identity.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.htv.smartfarm.common.exception.NotFoundException;
import com.htv.smartfarm.identity.account.domain.UserAccountEntity;
import com.htv.smartfarm.identity.account.repository.UserAccountRepository;
import com.htv.smartfarm.identity.authorization.application.FarmScopeService;
import com.htv.smartfarm.identity.tenant.domain.TenantEntity;
import com.htv.smartfarm.identity.tenant.domain.TenantMembershipEntity;
import com.htv.smartfarm.identity.tenant.repository.TenantMembershipRepository;
import com.htv.smartfarm.identity.tenant.repository.TenantRepository;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * V1 Farm authorization on REAL PostgreSQL (sprint §7/§32): proves farm isolation through the
 * real FarmScopeService + MembershipFarmRepository.
 *
 *   User A granted Farm A  -> Farm A ALLOWED (in A's farm scope), Farm B DENIED (not in scope)
 *   Grant for an UNKNOWN farm -> rejected (fail-closed FarmDirectoryPort), i.e. no silent grant.
 *
 * The fail-closed production fallback is replaced in this test by a FarmDirectoryPort that knows
 * only farm-A / farm-B (see AbstractIdentityPostgresIT), so an unknown farm is still denied.
 */
class FarmAuthorizationIT extends AbstractIdentityPostgresIT {

    @Autowired private FarmScopeService farmScope;
    @Autowired private TenantRepository tenants;
    @Autowired private UserAccountRepository accounts;
    @Autowired private TenantMembershipRepository memberships;

    private String seedActiveMembership(String tenantId, String userId) {
        tenants.save(new TenantEntity(tenantId, "code-" + tenantId, "Tenant " + tenantId));
        UserAccountEntity account = accounts.save(
                new UserAccountEntity(userId, userId + "@it.test", "x".repeat(40)));
        TenantEntity tenant = tenants.findById(tenantId).orElseThrow();
        TenantMembershipEntity m = new TenantMembershipEntity(UUID.randomUUID().toString(), tenant, account);
        m.activate();
        memberships.save(m);
        return m.getId();
    }

    @Test
    void userGrantedFarmAIsAllowedOnFarmAAndDeniedOnFarmB() {
        String tenant = "tenant-auth-" + Long.toString(System.nanoTime(), 36);
        String userA = "userA-" + Long.toString(System.nanoTime(), 36);
        seedActiveMembership(tenant, userA);

        // Grant access to farm-A (known farm -> fail-closed port allows it).
        farmScope.grantFarmAccess(tenant, userA, "farm-A");

        var scope = farmScope.getFarmIds(tenant, userA);
        assertThat(scope).contains("farm-A");   // ALLOWED
        assertThat(scope).doesNotContain("farm-B"); // DENIED (never granted)
    }

    @Test
    void grantForUnknownFarmIsRejectedFailClosed() {
        String tenant = "tenant-auth-" + Long.toString(System.nanoTime(), 36);
        String userA = "userA-" + Long.toString(System.nanoTime(), 36);
        seedActiveMembership(tenant, userA);

        // farm-UNKNOWN is not in the directory -> grant must be rejected, not silently allowed.
        assertThatThrownBy(() -> farmScope.grantFarmAccess(tenant, userA, "farm-UNKNOWN"))
                .isInstanceOf(NotFoundException.class);
        assertThat(farmScope.getFarmIds(tenant, userA)).doesNotContain("farm-UNKNOWN");
    }

    @Test
    void revokeRemovesFarmFromScope() {
        String tenant = "tenant-auth-" + Long.toString(System.nanoTime(), 36);
        String userA = "userA-" + Long.toString(System.nanoTime(), 36);
        seedActiveMembership(tenant, userA);

        farmScope.grantFarmAccess(tenant, userA, "farm-A");
        assertThat(farmScope.getFarmIds(tenant, userA)).contains("farm-A");

        farmScope.revokeFarmAccess(tenant, userA, "farm-A");
        assertThat(farmScope.getFarmIds(tenant, userA)).doesNotContain("farm-A"); // DENIED after revoke
    }
}
