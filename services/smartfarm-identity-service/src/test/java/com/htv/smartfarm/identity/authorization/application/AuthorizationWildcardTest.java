package com.htv.smartfarm.identity.authorization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import com.htv.smartfarm.identity.account.domain.UserAccountEntity;
import com.htv.smartfarm.identity.account.repository.UserAccountRepository;
import com.htv.smartfarm.identity.authorization.application.model.PermissionTarget;
import com.htv.smartfarm.identity.authorization.repository.*;
import com.htv.smartfarm.identity.tenant.domain.*;
import com.htv.smartfarm.identity.tenant.repository.TenantMembershipRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthorizationWildcardTest {
    @Mock UserAccountRepository accounts; @Mock TenantMembershipRepository memberships;
    @Mock MembershipRoleRepository roles; @Mock RolePermissionRepository permissions;
    @Mock MembershipFarmRepository farms; @Mock UserAccountEntity account;
    @Mock TenantMembershipEntity membership; @Mock TenantEntity tenant;

    @Test
    void protectedWildcardAllowsPermissionAndFarmScopeInsideAuthenticatedTenant() {
        Instant now = Instant.parse("2026-10-09T00:00:00Z");
        var service = new AuthorizationService(accounts, memberships, roles, permissions, farms,
                Clock.fixed(now, ZoneOffset.UTC));
        when(accounts.findById("root")).thenReturn(Optional.of(account));
        when(account.isEnabled()).thenReturn(true); when(account.isLoginAllowed(now)).thenReturn(true);
        when(memberships.findByTenantIdAndUserId("system", "root")).thenReturn(Optional.of(membership));
        when(membership.getTenant()).thenReturn(tenant); when(tenant.isEnabled()).thenReturn(true);
        when(membership.getStatus()).thenReturn(MembershipStatus.ACTIVE); when(membership.getId()).thenReturn("m1");
        when(roles.findRoleIdsByMembershipId("m1")).thenReturn(List.of("super-role"));
        when(permissions.findPermissionCodesByRoleIds(List.of("super-role"))).thenReturn(List.of("*"));
        when(farms.findFarmIdsByMembershipId("m1")).thenReturn(List.of());
        var decision = service.checkPermission("system", "root",
                new PermissionTarget("FARM", "any-farm", "READ"));
        assertThat(decision.allowed()).isTrue();
    }
}
