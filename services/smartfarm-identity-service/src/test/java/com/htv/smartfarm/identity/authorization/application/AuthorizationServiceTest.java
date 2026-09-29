package com.htv.smartfarm.identity.authorization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.htv.smartfarm.identity.account.domain.UserAccountEntity;
import com.htv.smartfarm.identity.account.repository.UserAccountRepository;
import com.htv.smartfarm.identity.authorization.application.model.PermissionDecision;
import com.htv.smartfarm.identity.authorization.application.model.PermissionTarget;
import com.htv.smartfarm.identity.authorization.repository.MembershipFarmRepository;
import com.htv.smartfarm.identity.authorization.repository.MembershipRoleRepository;
import com.htv.smartfarm.identity.authorization.repository.RolePermissionRepository;
import com.htv.smartfarm.identity.tenant.domain.MembershipStatus;
import com.htv.smartfarm.identity.tenant.domain.TenantEntity;
import com.htv.smartfarm.identity.tenant.domain.TenantMembershipEntity;
import com.htv.smartfarm.identity.tenant.repository.TenantMembershipRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthorizationServiceTest {

    private static final String TENANT_ID = "tenant-001";
    private static final String SUBJECT_ID = "user-001";
    private static final String MEMBERSHIP_ID = "membership-001";
    private static final String FARM_ID = "farm-001";

    private static final Instant NOW =
            Instant.parse("2026-09-29T08:00:00Z");

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private TenantMembershipRepository membershipRepository;

    @Mock
    private MembershipRoleRepository membershipRoleRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private MembershipFarmRepository membershipFarmRepository;

    @Mock
    private UserAccountEntity account;

    @Mock
    private TenantMembershipEntity membership;

    @Mock
    private TenantEntity tenant;

    private AuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                NOW,
                ZoneOffset.UTC
        );

        authorizationService = new AuthorizationService(
                userAccountRepository,
                membershipRepository,
                membershipRoleRepository,
                rolePermissionRepository,
                membershipFarmRepository,
                clock
        );
    }

    @Test
    void checkPermission_shouldAllow_whenRoleAndFarmScopeAreGranted() {
        PermissionTarget target = farmTarget(
                FARM_ID,
                "READ"
        );

        prepareActiveAccountAndMembership();

        when(membershipRoleRepository.findRoleIdsByMembershipId(
                MEMBERSHIP_ID
        )).thenReturn(List.of("role-001"));

        when(rolePermissionRepository.findPermissionCodesByRoleIds(
                List.of("role-001")
        )).thenReturn(List.of("FARM:READ"));

        when(membershipFarmRepository.findFarmIdsByMembershipId(
                MEMBERSHIP_ID
        )).thenReturn(List.of(FARM_ID));

        PermissionDecision decision =
                authorizationService.checkPermission(
                        TENANT_ID,
                        SUBJECT_ID,
                        target
                );

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reasonCode())
                .isEqualTo("ALLOWED_BY_ROLE");
        assertThat(decision.target()).isEqualTo(target);
    }

    @Test
    void checkPermission_shouldDeny_whenAccountDoesNotExist() {
        PermissionTarget target = farmTarget(
                FARM_ID,
                "READ"
        );

        when(userAccountRepository.findById(SUBJECT_ID))
                .thenReturn(Optional.empty());

        PermissionDecision decision =
                authorizationService.checkPermission(
                        TENANT_ID,
                        SUBJECT_ID,
                        target
                );

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode())
                .isEqualTo("ACCOUNT_NOT_FOUND");

        verifyNoInteractions(
                membershipRepository,
                membershipRoleRepository,
                rolePermissionRepository,
                membershipFarmRepository
        );
    }

    @Test
    void checkPermission_shouldDeny_whenAccountIsDisabled() {
        PermissionTarget target = farmTarget(
                FARM_ID,
                "READ"
        );

        when(userAccountRepository.findById(SUBJECT_ID))
                .thenReturn(Optional.of(account));

        when(account.isEnabled()).thenReturn(false);

        PermissionDecision decision =
                authorizationService.checkPermission(
                        TENANT_ID,
                        SUBJECT_ID,
                        target
                );

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode())
                .isEqualTo("ACCOUNT_DISABLED");

        verifyNoInteractions(
                membershipRepository,
                membershipRoleRepository,
                rolePermissionRepository,
                membershipFarmRepository
        );
    }

    @Test
    void checkPermission_shouldDeny_whenAccountIsLocked() {
        PermissionTarget target = farmTarget(
                FARM_ID,
                "READ"
        );

        when(userAccountRepository.findById(SUBJECT_ID))
                .thenReturn(Optional.of(account));

        when(account.isEnabled()).thenReturn(true);
        when(account.isLoginAllowed(NOW)).thenReturn(false);

        PermissionDecision decision =
                authorizationService.checkPermission(
                        TENANT_ID,
                        SUBJECT_ID,
                        target
                );

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode())
                .isEqualTo("ACCOUNT_LOCKED");
    }

    @Test
    void checkPermission_shouldDeny_whenMembershipDoesNotExist() {
        PermissionTarget target = farmTarget(
                FARM_ID,
                "READ"
        );

        when(userAccountRepository.findById(SUBJECT_ID))
                .thenReturn(Optional.of(account));

        when(account.isEnabled()).thenReturn(true);
        when(account.isLoginAllowed(NOW)).thenReturn(true);

        when(membershipRepository.findByTenantIdAndUserId(
                TENANT_ID,
                SUBJECT_ID
        )).thenReturn(Optional.empty());

        PermissionDecision decision =
                authorizationService.checkPermission(
                        TENANT_ID,
                        SUBJECT_ID,
                        target
                );

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode())
                .isEqualTo("MEMBERSHIP_NOT_FOUND");
    }

    @Test
    void checkPermission_shouldDeny_whenTenantIsDisabled() {
        PermissionTarget target = farmTarget(
                FARM_ID,
                "READ"
        );

        prepareExistingAccount();

        when(membershipRepository.findByTenantIdAndUserId(
                TENANT_ID,
                SUBJECT_ID
        )).thenReturn(Optional.of(membership));

        when(membership.getTenant()).thenReturn(tenant);
        when(tenant.isEnabled()).thenReturn(false);

        PermissionDecision decision =
                authorizationService.checkPermission(
                        TENANT_ID,
                        SUBJECT_ID,
                        target
                );

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode())
                .isEqualTo("TENANT_DISABLED");
    }

    @Test
    void checkPermission_shouldDeny_whenMembershipIsSuspended() {
        PermissionTarget target = farmTarget(
                FARM_ID,
                "READ"
        );

        prepareExistingAccount();

        when(membershipRepository.findByTenantIdAndUserId(
                TENANT_ID,
                SUBJECT_ID
        )).thenReturn(Optional.of(membership));

        when(membership.getTenant()).thenReturn(tenant);
        when(tenant.isEnabled()).thenReturn(true);

        when(membership.getStatus())
                .thenReturn(MembershipStatus.SUSPENDED);

        PermissionDecision decision =
                authorizationService.checkPermission(
                        TENANT_ID,
                        SUBJECT_ID,
                        target
                );

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode())
                .isEqualTo("MEMBERSHIP_SUSPENDED");
    }

    @Test
    void checkPermission_shouldDeny_whenNoRoleIsAssigned() {
        PermissionTarget target = farmTarget(
                FARM_ID,
                "READ"
        );

        prepareActiveAccountAndMembership();

        when(membershipRoleRepository.findRoleIdsByMembershipId(
                MEMBERSHIP_ID
        )).thenReturn(List.of());

        PermissionDecision decision =
                authorizationService.checkPermission(
                        TENANT_ID,
                        SUBJECT_ID,
                        target
                );

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode())
                .isEqualTo("ROLE_NOT_GRANTED");
    }

    @Test
    void checkPermission_shouldDeny_whenPermissionIsNotGranted() {
        PermissionTarget target = farmTarget(
                FARM_ID,
                "UPDATE"
        );

        prepareActiveAccountAndMembership();

        when(membershipRoleRepository.findRoleIdsByMembershipId(
                MEMBERSHIP_ID
        )).thenReturn(List.of("role-001"));

        when(rolePermissionRepository.findPermissionCodesByRoleIds(
                List.of("role-001")
        )).thenReturn(List.of("FARM:READ"));

        when(membershipFarmRepository.findFarmIdsByMembershipId(
                MEMBERSHIP_ID
        )).thenReturn(List.of(FARM_ID));

        PermissionDecision decision =
                authorizationService.checkPermission(
                        TENANT_ID,
                        SUBJECT_ID,
                        target
                );

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode())
                .isEqualTo("PERMISSION_NOT_GRANTED");
    }

    @Test
    void checkPermission_shouldDeny_whenFarmIsOutsideMembershipScope() {
        PermissionTarget target = farmTarget(
                "farm-not-assigned",
                "READ"
        );

        prepareActiveAccountAndMembership();

        when(membershipRoleRepository.findRoleIdsByMembershipId(
                MEMBERSHIP_ID
        )).thenReturn(List.of("role-001"));

        when(rolePermissionRepository.findPermissionCodesByRoleIds(
                List.of("role-001")
        )).thenReturn(List.of("FARM:READ"));

        when(membershipFarmRepository.findFarmIdsByMembershipId(
                MEMBERSHIP_ID
        )).thenReturn(List.of(FARM_ID));

        PermissionDecision decision =
                authorizationService.checkPermission(
                        TENANT_ID,
                        SUBJECT_ID,
                        target
                );

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode())
                .isEqualTo("RESOURCE_OUT_OF_SCOPE");
    }

    @Test
    void checkPermission_shouldAllowNonFarmResource_withoutFarmScope() {
        PermissionTarget target = new PermissionTarget(
                "USER",
                "user-002",
                "READ"
        );

        prepareActiveAccountAndMembership();

        when(membershipRoleRepository.findRoleIdsByMembershipId(
                MEMBERSHIP_ID
        )).thenReturn(List.of("role-001"));

        when(rolePermissionRepository.findPermissionCodesByRoleIds(
                List.of("role-001")
        )).thenReturn(List.of("USER:READ"));

        when(membershipFarmRepository.findFarmIdsByMembershipId(
                MEMBERSHIP_ID
        )).thenReturn(List.of());

        PermissionDecision decision =
                authorizationService.checkPermission(
                        TENANT_ID,
                        SUBJECT_ID,
                        target
                );

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reasonCode())
                .isEqualTo("ALLOWED_BY_ROLE");
    }

    @Test
    void batchCheckPermissions_shouldEvaluateAllTargets_fromSingleContext() {
        PermissionTarget readFarm = farmTarget(
                FARM_ID,
                "READ"
        );

        PermissionTarget updateFarm = farmTarget(
                FARM_ID,
                "UPDATE"
        );

        PermissionTarget readUser = new PermissionTarget(
                "USER",
                "user-002",
                "READ"
        );

        prepareActiveAccountAndMembership();

        when(membershipRoleRepository.findRoleIdsByMembershipId(
                MEMBERSHIP_ID
        )).thenReturn(List.of("role-001"));

        when(rolePermissionRepository.findPermissionCodesByRoleIds(
                List.of("role-001")
        )).thenReturn(List.of(
                "FARM:READ",
                "USER:READ"
        ));

        when(membershipFarmRepository.findFarmIdsByMembershipId(
                MEMBERSHIP_ID
        )).thenReturn(List.of(FARM_ID));

        List<PermissionDecision> decisions =
                authorizationService.batchCheckPermissions(
                        TENANT_ID,
                        SUBJECT_ID,
                        List.of(
                                readFarm,
                                updateFarm,
                                readUser
                        )
                );

        assertThat(decisions).hasSize(3);

        assertThat(decisions.get(0).allowed()).isTrue();
        assertThat(decisions.get(0).reasonCode())
                .isEqualTo("ALLOWED_BY_ROLE");

        assertThat(decisions.get(1).allowed()).isFalse();
        assertThat(decisions.get(1).reasonCode())
                .isEqualTo("PERMISSION_NOT_GRANTED");

        assertThat(decisions.get(2).allowed()).isTrue();
        assertThat(decisions.get(2).reasonCode())
                .isEqualTo("ALLOWED_BY_ROLE");
    }

    @Test
    void batchCheckPermissions_shouldReturnEmptyList_whenTargetsAreEmpty() {
        List<PermissionDecision> decisions =
                authorizationService.batchCheckPermissions(
                        TENANT_ID,
                        SUBJECT_ID,
                        List.of()
                );

        assertThat(decisions).isEmpty();

        verifyNoInteractions(
                userAccountRepository,
                membershipRepository,
                membershipRoleRepository,
                rolePermissionRepository,
                membershipFarmRepository
        );
    }

    @Test
    void batchCheckPermissions_shouldRejectMoreThanOneHundredTargets() {
        List<PermissionTarget> targets =
                java.util.stream.IntStream
                        .range(0, 101)
                        .mapToObj(index -> new PermissionTarget(
                                "USER",
                                "user-" + index,
                                "READ"
                        ))
                        .toList();

        assertThatThrownBy(() ->
                authorizationService.batchCheckPermissions(
                        TENANT_ID,
                        SUBJECT_ID,
                        targets
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "maximum of 100 permission targets"
                );
    }

    @Test
    void checkPermission_shouldRejectFarmTargetWithoutResourceId() {
        PermissionTarget target = new PermissionTarget(
                "FARM",
                null,
                "READ"
        );

        assertThatThrownBy(() ->
                authorizationService.checkPermission(
                        TENANT_ID,
                        SUBJECT_ID,
                        target
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("resourceId must not be blank");
    }

    @Test
    void checkPermission_shouldNormalizePermissionCode() {
        PermissionTarget target = new PermissionTarget(
                " farm ",
                FARM_ID,
                " read "
        );

        prepareActiveAccountAndMembership();

        when(membershipRoleRepository.findRoleIdsByMembershipId(
                MEMBERSHIP_ID
        )).thenReturn(List.of("role-001"));

        when(rolePermissionRepository.findPermissionCodesByRoleIds(
                List.of("role-001")
        )).thenReturn(List.of("farm:read"));

        when(membershipFarmRepository.findFarmIdsByMembershipId(
                MEMBERSHIP_ID
        )).thenReturn(List.of(FARM_ID));

        PermissionDecision decision =
                authorizationService.checkPermission(
                        TENANT_ID,
                        SUBJECT_ID,
                        target
                );

        assertThat(decision.allowed()).isTrue();
    }

    private void prepareExistingAccount() {
        when(userAccountRepository.findById(SUBJECT_ID))
                .thenReturn(Optional.of(account));

        when(account.isEnabled()).thenReturn(true);
        when(account.isLoginAllowed(NOW)).thenReturn(true);
    }

    private void prepareActiveAccountAndMembership() {
        prepareExistingAccount();

        when(membershipRepository.findByTenantIdAndUserId(
                TENANT_ID,
                SUBJECT_ID
        )).thenReturn(Optional.of(membership));

        when(membership.getTenant()).thenReturn(tenant);
        when(tenant.isEnabled()).thenReturn(true);

        when(membership.getStatus())
                .thenReturn(MembershipStatus.ACTIVE);

        when(membership.getId())
                .thenReturn(MEMBERSHIP_ID);
    }

    private PermissionTarget farmTarget(
            String farmId,
            String action
    ) {
        return new PermissionTarget(
                "FARM",
                farmId,
                action
        );
    }
}