package com.htv.smartfarm.identity.authorization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.htv.smartfarm.common.exception.ConflictException;
import com.htv.smartfarm.common.exception.NotFoundException;
import com.htv.smartfarm.common.exception.ValidationException;
import com.htv.smartfarm.identity.authorization.domain.MembershipFarmEntity;
import com.htv.smartfarm.identity.authorization.repository.MembershipFarmRepository;
import com.htv.smartfarm.identity.tenant.domain.MembershipStatus;
import com.htv.smartfarm.identity.tenant.domain.TenantMembershipEntity;
import com.htv.smartfarm.identity.tenant.repository.TenantMembershipRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FarmScopeServiceTest {

    private static final String TENANT_ID = "tenant-001";
    private static final String USER_ID = "user-001";
    private static final String FARM_ID = "farm-001";
    private static final String MEMBERSHIP_ID = "membership-001";

    @Mock
    private TenantMembershipRepository membershipRepository;

    @Mock
    private MembershipFarmRepository membershipFarmRepository;

    @Mock
    private FarmDirectoryPort farmDirectoryPort;

    @Mock
    private TenantMembershipEntity membership;

    private FarmScopeService service;

    @BeforeEach
    void setUp() {
        service = new FarmScopeService(
                membershipRepository,
                membershipFarmRepository,
                farmDirectoryPort
        );
    }

    @Test
    void grantFarmAccessShouldCreateScope() {
        prepareActiveMembershipWithId();

        when(farmDirectoryPort.existsInTenant(
                TENANT_ID,
                FARM_ID
        )).thenReturn(true);

        when(membershipFarmRepository.existsByMembershipIdAndFarmId(
                MEMBERSHIP_ID,
                FARM_ID
        )).thenReturn(false);

        service.grantFarmAccess(
                TENANT_ID,
                USER_ID,
                FARM_ID
        );

        verify(membershipFarmRepository).save(
                org.mockito.ArgumentMatchers.any(
                        MembershipFarmEntity.class
                )
        );
    }

    @Test
    void grantFarmAccessShouldBeIdempotent() {
        prepareActiveMembershipWithId();

        when(farmDirectoryPort.existsInTenant(
                TENANT_ID,
                FARM_ID
        )).thenReturn(true);

        when(membershipFarmRepository.existsByMembershipIdAndFarmId(
                MEMBERSHIP_ID,
                FARM_ID
        )).thenReturn(true);

        service.grantFarmAccess(
                TENANT_ID,
                USER_ID,
                FARM_ID
        );

        verify(
                membershipFarmRepository,
                never()
        ).save(
                org.mockito.ArgumentMatchers.any(
                        MembershipFarmEntity.class
                )
        );
    }

    @Test
    void grantFarmAccessShouldRejectInactiveMembership() {
        when(membershipRepository.findByTenantIdAndUserId(
                TENANT_ID,
                USER_ID
        )).thenReturn(Optional.of(membership));

        when(membership.getStatus())
                .thenReturn(MembershipStatus.SUSPENDED);

        assertThatThrownBy(() ->
                service.grantFarmAccess(
                        TENANT_ID,
                        USER_ID,
                        FARM_ID
                )
        )
                .isInstanceOf(ConflictException.class)
                .satisfies(exception -> assertThat(
                        ((ConflictException) exception).code()
                ).isEqualTo("MEMBERSHIP_NOT_ACTIVE"));

        verifyNoInteractions(
                farmDirectoryPort,
                membershipFarmRepository
        );
    }

    @Test
    void grantFarmAccessShouldRejectUnknownFarm() {
        prepareActiveMembership();

        when(farmDirectoryPort.existsInTenant(
                TENANT_ID,
                FARM_ID
        )).thenReturn(false);

        assertThatThrownBy(() ->
                service.grantFarmAccess(
                        TENANT_ID,
                        USER_ID,
                        FARM_ID
                )
        )
                .isInstanceOf(NotFoundException.class)
                .satisfies(exception -> assertThat(
                        ((NotFoundException) exception).code()
                ).isEqualTo("FARM_NOT_FOUND"));

        verify(farmDirectoryPort).existsInTenant(
                TENANT_ID,
                FARM_ID
        );

        verifyNoInteractions(membershipFarmRepository);
    }

    @Test
    void grantFarmAccessShouldRejectMissingMembership() {
        when(membershipRepository.findByTenantIdAndUserId(
                TENANT_ID,
                USER_ID
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.grantFarmAccess(
                        TENANT_ID,
                        USER_ID,
                        FARM_ID
                )
        )
                .isInstanceOf(NotFoundException.class)
                .satisfies(exception -> assertThat(
                        ((NotFoundException) exception).code()
                ).isEqualTo("TENANTMEMBERSHIP_NOT_FOUND"));

        verifyNoInteractions(
                farmDirectoryPort,
                membershipFarmRepository
        );
    }

    @Test
    void revokeFarmAccessShouldDeleteMapping() {
        when(membershipRepository.findByTenantIdAndUserId(
                TENANT_ID,
                USER_ID
        )).thenReturn(Optional.of(membership));

        when(membership.getId())
                .thenReturn(MEMBERSHIP_ID);

        service.revokeFarmAccess(
                TENANT_ID,
                USER_ID,
                FARM_ID
        );

        verify(
                membershipFarmRepository
        ).deleteByMembershipIdAndFarmId(
                MEMBERSHIP_ID,
                FARM_ID
        );
    }

    @Test
    void getFarmIdsShouldReturnAssignedFarms() {
        when(membershipRepository.findByTenantIdAndUserId(
                TENANT_ID,
                USER_ID
        )).thenReturn(Optional.of(membership));

        when(membership.getId())
                .thenReturn(MEMBERSHIP_ID);

        when(membershipFarmRepository.findFarmIdsByMembershipId(
                MEMBERSHIP_ID
        )).thenReturn(List.of(FARM_ID));

        assertThat(
                service.getFarmIds(
                        TENANT_ID,
                        USER_ID
                )
        ).containsExactly(FARM_ID);
    }

    @Test
    void getFarmIdsShouldRejectBlankTenant() {
        assertThatThrownBy(() ->
                service.getFarmIds(
                        " ",
                        USER_ID
                )
        )
                .isInstanceOf(ValidationException.class)
                .satisfies(exception -> assertThat(
                        ((ValidationException) exception).code()
                ).isEqualTo("REQUIRED_FIELD_MISSING"));

        verifyNoInteractions(
                membershipRepository,
                membershipFarmRepository,
                farmDirectoryPort
        );
    }

    private void prepareActiveMembership() {
        when(membershipRepository.findByTenantIdAndUserId(
                TENANT_ID,
                USER_ID
        )).thenReturn(Optional.of(membership));

        when(membership.getStatus())
                .thenReturn(MembershipStatus.ACTIVE);
    }

    private void prepareActiveMembershipWithId() {
        prepareActiveMembership();

        when(membership.getId())
                .thenReturn(MEMBERSHIP_ID);
    }
}