package com.htv.smartfarm.identity.tenant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.htv.smartfarm.common.exception.ConflictException;
import com.htv.smartfarm.common.exception.NotFoundException;
import com.htv.smartfarm.identity.account.domain.UserAccountEntity;
import com.htv.smartfarm.identity.account.repository.UserAccountRepository;
import com.htv.smartfarm.identity.authorization.repository.MembershipFarmRepository;
import com.htv.smartfarm.identity.authorization.repository.MembershipRoleRepository;
import com.htv.smartfarm.identity.tenant.domain.MembershipStatus;
import com.htv.smartfarm.identity.tenant.domain.TenantEntity;
import com.htv.smartfarm.identity.tenant.domain.TenantMembershipEntity;
import com.htv.smartfarm.identity.tenant.repository.TenantMembershipRepository;
import com.htv.smartfarm.identity.tenant.repository.TenantRepository;
import com.htv.smartfarm.identity.messaging.event.IdentityIntegrationEventPublisher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantMembershipServiceTest {

    private static final String TENANT_ID = "tenant-001";
    private static final String USER_ID = "user-001";
    private static final String MEMBERSHIP_ID = "membership-001";

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private TenantMembershipRepository membershipRepository;

    @Mock
    private MembershipRoleRepository membershipRoleRepository;

    @Mock
    private MembershipFarmRepository membershipFarmRepository;

    @Mock
    private IdentityIntegrationEventPublisher events;

    @Mock
    private TenantEntity tenant;

    @Mock
    private UserAccountEntity user;

    @Mock
    private TenantMembershipEntity membership;

    private TenantMembershipService service;

    @BeforeEach
    void setUp() {
        service = new TenantMembershipService(
                tenantRepository,
                userAccountRepository,
                membershipRepository,
                membershipRoleRepository,
                membershipFarmRepository,
                events
        );
    }

    @Test
    void inviteUserShouldCreateMembership() {
        when(tenantRepository.findById(TENANT_ID))
                .thenReturn(Optional.of(tenant));
        when(tenant.isEnabled()).thenReturn(true);
        when(userAccountRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));
        when(membershipRepository.existsByTenantIdAndUserId(
                TENANT_ID,
                USER_ID
        )).thenReturn(false);
        when(membershipRepository.save(
                org.mockito.ArgumentMatchers.any(
                        TenantMembershipEntity.class
                )
        )).thenAnswer(invocation -> invocation.getArgument(0));

        String membershipId = service.inviteUser(
                TENANT_ID,
                USER_ID
        );

        assertThat(membershipId).isNotBlank();
        verify(membershipRepository).save(
                org.mockito.ArgumentMatchers.any(
                        TenantMembershipEntity.class
                )
        );
    }

    @Test
    void inviteUserShouldRejectExistingMembership() {
        when(tenantRepository.findById(TENANT_ID))
                .thenReturn(Optional.of(tenant));
        when(tenant.isEnabled()).thenReturn(true);
        when(userAccountRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));
        when(membershipRepository.existsByTenantIdAndUserId(
                TENANT_ID,
                USER_ID
        )).thenReturn(true);

        assertThatThrownBy(() -> service.inviteUser(
                TENANT_ID,
                USER_ID
        ))
                .isInstanceOf(ConflictException.class)
                .satisfies(exception -> assertThat(
                        ((ConflictException) exception).code()
                ).isEqualTo("TENANT_MEMBERSHIP_ALREADY_EXISTS"));
    }

    @Test
    void inviteUserShouldRejectDisabledTenant() {
        when(tenantRepository.findById(TENANT_ID))
                .thenReturn(Optional.of(tenant));
        when(tenant.isEnabled()).thenReturn(false);

        assertThatThrownBy(() -> service.inviteUser(
                TENANT_ID,
                USER_ID
        ))
                .isInstanceOf(ConflictException.class)
                .satisfies(exception -> assertThat(
                        ((ConflictException) exception).code()
                ).isEqualTo("TENANT_DISABLED"));

        verifyNoInteractions(userAccountRepository);
    }

    @Test
    void getMembershipShouldReturnRolesAndFarms() {
        when(membershipRepository.findByTenantIdAndUserId(
                TENANT_ID,
                USER_ID
        )).thenReturn(Optional.of(membership));
        when(membership.getId()).thenReturn(MEMBERSHIP_ID);
        when(membership.getTenant()).thenReturn(tenant);
        when(tenant.getId()).thenReturn(TENANT_ID);
        when(membership.getStatus())
                .thenReturn(MembershipStatus.ACTIVE);
        when(membershipRoleRepository.findRoleCodesByMembershipId(
                MEMBERSHIP_ID
        )).thenReturn(List.of("ADMIN"));
        when(membershipFarmRepository.findFarmIdsByMembershipId(
                MEMBERSHIP_ID
        )).thenReturn(List.of("farm-001"));

        var result = service.getMembership(
                TENANT_ID,
                USER_ID
        );

        assertThat(result.membershipId())
                .isEqualTo(MEMBERSHIP_ID);
        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.roles()).containsExactly("ADMIN");
        assertThat(result.farmIds()).containsExactly("farm-001");
    }

    @Test
    void getMembershipShouldThrowNotFound() {
        when(membershipRepository.findByTenantIdAndUserId(
                TENANT_ID,
                USER_ID
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMembership(
                TENANT_ID,
                USER_ID
        ))
                .isInstanceOf(NotFoundException.class)
                .satisfies(exception -> assertThat(
                        ((NotFoundException) exception).code()
                ).isEqualTo("TENANTMEMBERSHIP_NOT_FOUND"));
    }

    @Test
    void activateMembershipShouldLockAndActivateMembership() {
        when(membershipRepository.findByTenantAndUserForUpdate(
                TENANT_ID,
                USER_ID
        )).thenReturn(Optional.of(membership));

        service.activateMembership(TENANT_ID, USER_ID);

        verify(membership).activate();
    }

    @Test
    void findUserMembershipsShouldRejectBlankUserId() {
        assertThatThrownBy(() -> service.findUserMemberships(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId must not be blank");
    }
}
