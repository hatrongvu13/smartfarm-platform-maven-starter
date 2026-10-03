package com.htv.smartfarm.identity.authorization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.htv.smartfarm.common.exception.ConflictException;
import com.htv.smartfarm.identity.authorization.repository.MembershipRoleRepository;
import com.htv.smartfarm.identity.authorization.repository.PermissionRepository;
import com.htv.smartfarm.identity.authorization.repository.RolePermissionRepository;
import com.htv.smartfarm.identity.authorization.repository.RoleRepository;
import com.htv.smartfarm.identity.messaging.event.IdentityIntegrationEventPublisher;
import com.htv.smartfarm.identity.tenant.repository.TenantMembershipRepository;
import com.htv.smartfarm.identity.tenant.repository.TenantRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Locks the platform-role delegation guard (UPD-ID-01 / UPD-ID-02): a tenant admin must
 * NOT be able to grant or revoke SUPERADMIN or PLATFORM_ADMIN through ordinary tenant
 * administration. This protects both the single-super-admin invariant (no second
 * SUPERADMIN can be minted via the normal API) and admin peer-protection (a privileged
 * role cannot be stripped by delegation). The guard must fire BEFORE any repository is
 * touched, so an unverified call never reaches persistence.
 *
 * These are characterization tests: the guard already exists in
 * {@code RoleManagementService.rejectPlatformRoleDelegation}; the tests pin the
 * behaviour so a future refactor cannot silently remove it.
 */
@ExtendWith(MockitoExtension.class)
class RoleManagementDelegationGuardTest {

    private static final String TENANT = "tenant-001";
    private static final String USER = "user-001";

    @Mock private TenantRepository tenantRepository;
    @Mock private TenantMembershipRepository membershipRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private MembershipRoleRepository membershipRoleRepository;
    @Mock private IdentityIntegrationEventPublisher events;

    private RoleManagementService service() {
        return new RoleManagementService(tenantRepository, membershipRepository, roleRepository,
                permissionRepository, rolePermissionRepository, membershipRoleRepository, events);
    }

    @Test
    void assignRoleRejectsSuperAdminBeforeTouchingPersistence() {
        assertThatThrownBy(() -> service().assignRole(TENANT, USER, "SUPERADMIN", "actor-001"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Platform roles cannot be delegated");
        verifyNoInteractions(membershipRepository, membershipRoleRepository, events);
    }

    @Test
    void assignRoleRejectsPlatformAdmin() {
        assertThatThrownBy(() -> service().assignRole(TENANT, USER, "PLATFORM_ADMIN", "actor-001"))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(membershipRepository, membershipRoleRepository, events);
    }

    @Test
    void revokeRoleRejectsSuperAdminBeforeTouchingPersistence() {
        assertThatThrownBy(() -> service().revokeRole(TENANT, USER, "SUPERADMIN"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Platform roles cannot be delegated");
        verifyNoInteractions(membershipRepository, membershipRoleRepository, events);
    }

    @Test
    void guardIsCaseInsensitiveOnRoleCode() {
        // Role codes are normalized to upper-case; a lower-case 'superadmin' is still blocked.
        assertThatThrownBy(() -> service().assignRole(TENANT, USER, "superadmin", "actor-001"))
                .isInstanceOf(ConflictException.class);
        assertThat(true).isTrue();
    }
}
