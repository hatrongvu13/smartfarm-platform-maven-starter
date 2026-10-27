package com.htv.farmverse.identity.authorization.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.htv.farmverse.common.exception.ConflictException;
import com.htv.farmverse.identity.authorization.domain.RoleEntity;
import com.htv.farmverse.identity.authorization.repository.MembershipRoleRepository;
import com.htv.farmverse.identity.authorization.repository.PermissionRepository;
import com.htv.farmverse.identity.authorization.repository.RolePermissionRepository;
import com.htv.farmverse.identity.authorization.repository.RoleRepository;
import com.htv.farmverse.identity.authorization.version.AuthorizationMutationCoordinator;
import com.htv.farmverse.identity.messaging.event.IdentityIntegrationEventPublisher;
import com.htv.farmverse.identity.tenant.domain.MembershipStatus;
import com.htv.farmverse.identity.tenant.domain.TenantEntity;
import com.htv.farmverse.identity.tenant.domain.TenantMembershipEntity;
import com.htv.farmverse.identity.tenant.repository.TenantMembershipRepository;
import com.htv.farmverse.identity.tenant.repository.TenantRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoleManagementInitialAssignmentTest {

    private final TenantMembershipRepository memberships =
            mock(TenantMembershipRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final MembershipRoleRepository membershipRoles =
            mock(MembershipRoleRepository.class);
    private final IdentityIntegrationEventPublisher events =
            mock(IdentityIntegrationEventPublisher.class);
    private final AuthorizationMutationCoordinator authorizationMutations =
            mock(AuthorizationMutationCoordinator.class);
    private final TenantMembershipEntity membership =
            mock(TenantMembershipEntity.class);
    private final TenantEntity tenant = mock(TenantEntity.class);
    private final RoleEntity role = mock(RoleEntity.class);

    private RoleManagementService service;

    @BeforeEach
    void setUp() {
        service = new RoleManagementService(
                mock(TenantRepository.class),
                memberships,
                roles,
                mock(PermissionRepository.class),
                mock(RolePermissionRepository.class),
                membershipRoles,
                events,
                authorizationMutations
        );
        when(memberships.findByTenantAndUserForUpdate("tenant-1", "user-1"))
                .thenReturn(Optional.of(membership));
        when(membership.getId()).thenReturn("membership-1");
        when(membership.getTenant()).thenReturn(tenant);
        when(tenant.getId()).thenReturn("tenant-1");
        when(roles.findByTenantIdAndCode("tenant-1", "USER"))
                .thenReturn(Optional.of(role));
        when(role.getTenant()).thenReturn(tenant);
        when(role.getId()).thenReturn("role-1");
        when(role.getCode()).thenReturn("USER");
        when(membershipRoles.existsByIdMembershipIdAndIdRoleId(
                "membership-1",
                "role-1"
        )).thenReturn(false);
        when(events.publish(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(Long.class),
                any()
        )).thenReturn("event-1");
    }

    @Test
    void initialRoleAssignmentAllowsInvitedMembershipAndInvalidatesAuth() {
        when(membership.getStatus()).thenReturn(MembershipStatus.INVITED);

        service.assignInitialRole("tenant-1", "user-1", "user", "actor-1");

        verify(membershipRoles).save(any());
        verify(authorizationMutations).subjectChanged(
                eq("tenant-1"),
                eq("user-1"),
                eq("actor-1"),
                eq(null),
                eq("ROLE_ASSIGNED")
        );
    }

    @Test
    void regularRoleAssignmentStillRejectsInvitedMembership() {
        when(membership.getStatus()).thenReturn(MembershipStatus.INVITED);

        assertThatThrownBy(() ->
                service.assignRole("tenant-1", "user-1", "USER", "actor-1")
        ).isInstanceOf(ConflictException.class);

        verify(membershipRoles, never()).save(any());
        verify(events, never()).publish(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(Long.class),
                any()
        );
    }
}
