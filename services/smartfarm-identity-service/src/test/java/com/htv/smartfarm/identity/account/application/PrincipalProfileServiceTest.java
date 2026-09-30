package com.htv.smartfarm.identity.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.htv.smartfarm.common.exception.NotFoundException;
import com.htv.smartfarm.common.exception.ValidationException;
import com.htv.smartfarm.identity.account.application.command.UpdateProfileCommand;
import com.htv.smartfarm.identity.account.domain.UserAccountEntity;
import com.htv.smartfarm.identity.account.domain.UserProfileEntity;
import com.htv.smartfarm.identity.account.repository.UserProfileRepository;
import com.htv.smartfarm.identity.authorization.repository.MembershipFarmRepository;
import com.htv.smartfarm.identity.authorization.repository.MembershipRoleRepository;
import com.htv.smartfarm.identity.shared.exception.OptimisticConflictException;
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
class PrincipalProfileServiceTest {

    private static final String TENANT_ID = "tenant-001";
    private static final String SUBJECT_ID = "user-001";
    private static final String MEMBERSHIP_ID = "membership-001";

    @Mock
    private UserProfileRepository profileRepository;

    @Mock
    private TenantMembershipRepository membershipRepository;

    @Mock
    private MembershipRoleRepository membershipRoleRepository;

    @Mock
    private MembershipFarmRepository membershipFarmRepository;

    @Mock
    private UserProfileEntity profile;

    @Mock
    private UserAccountEntity account;

    @Mock
    private TenantMembershipEntity membership;

    @Mock
    private TenantEntity tenant;

    private PrincipalProfileService service;

    @BeforeEach
    void setUp() {
        service = new PrincipalProfileService(
                profileRepository,
                membershipRepository,
                membershipRoleRepository,
                membershipFarmRepository
        );
    }

    @Test
    void getPrincipalShouldMapProfileAndMembership() {
        preparePrincipalData();

        var result = service.getPrincipal(TENANT_ID, SUBJECT_ID);

        assertThat(result.subjectId()).isEqualTo(SUBJECT_ID);
        assertThat(result.membership().tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.membership().roles()).containsExactly("ADMIN");
    }

    @Test
    void getPrincipalShouldThrowWhenProfileMissing() {
        when(profileRepository.findWithAccountByUserId(SUBJECT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPrincipal(
                TENANT_ID,
                SUBJECT_ID
        ))
                .isInstanceOf(NotFoundException.class)
                .satisfies(exception -> assertThat(
                        ((NotFoundException) exception).code()
                ).isEqualTo("USERPROFILE_NOT_FOUND"));
    }

    @Test
    void updateProfileShouldRejectVersionMismatch() {
        UpdateProfileCommand command = new UpdateProfileCommand(
                SUBJECT_ID,
                "Updated Name",
                null,
                null,
                null,
                Set.of("display_name"),
                4L
        );

        when(profileRepository.findWithAccountByUserId(SUBJECT_ID))
                .thenReturn(Optional.of(profile));
        when(profile.getVersion()).thenReturn(5L);

        assertThatThrownBy(() -> service.updateProfile(
                TENANT_ID,
                command
        )).isInstanceOf(OptimisticConflictException.class);
    }

    @Test
    void updateProfileShouldRejectUnsupportedField() {
        UpdateProfileCommand command = new UpdateProfileCommand(
                SUBJECT_ID,
                null,
                null,
                null,
                null,
                Set.of("email"),
                0L
        );

        assertThatThrownBy(() -> service.updateProfile(
                TENANT_ID,
                command
        ))
                .isInstanceOf(ValidationException.class)
                .satisfies(exception -> assertThat(
                        ((ValidationException) exception).code()
                ).isEqualTo("UPDATE_FIELDS_UNSUPPORTED"));
    }

    @Test
    void updateProfileShouldUpdateSelectedField() {
        UpdateProfileCommand command = new UpdateProfileCommand(
                SUBJECT_ID,
                " Updated Name ",
                null,
                null,
                null,
                Set.of("display_name"),
                5L
        );

        preparePrincipalData();
        when(profile.getVersion()).thenReturn(5L);
        when(profile.getPhoneNumber()).thenReturn("0900000000");
        when(profile.getLocale()).thenReturn("vi-VN");
        when(profile.getTimeZone()).thenReturn("Asia/Ho_Chi_Minh");

        service.updateProfile(TENANT_ID, command);

        verify(profile).update(
                "Updated Name",
                "0900000000",
                "vi-VN",
                "Asia/Ho_Chi_Minh"
        );
        verify(profileRepository).flush();
    }

    private void preparePrincipalData() {
        when(profileRepository.findWithAccountByUserId(SUBJECT_ID))
                .thenReturn(Optional.of(profile));
        when(membershipRepository.findByTenantIdAndUserId(
                TENANT_ID,
                SUBJECT_ID
        )).thenReturn(Optional.of(membership));
        when(profile.getAccount()).thenReturn(account);
        when(account.getId()).thenReturn(SUBJECT_ID);
        when(account.getEmail()).thenReturn("user@example.com");
        when(account.isEnabled()).thenReturn(true);
        when(profile.getDisplayName()).thenReturn("User Name");
        when(profile.getLocale()).thenReturn("vi-VN");
        when(profile.getTimeZone()).thenReturn("Asia/Ho_Chi_Minh");
        when(membership.getId()).thenReturn(MEMBERSHIP_ID);
        when(membership.getTenant()).thenReturn(tenant);
        when(tenant.getId()).thenReturn(TENANT_ID);
        when(membership.getStatus()).thenReturn(MembershipStatus.ACTIVE);
        when(membershipRoleRepository.findRoleCodesByMembershipId(
                MEMBERSHIP_ID
        )).thenReturn(List.of("ADMIN"));
        when(membershipFarmRepository.findFarmIdsByMembershipId(
                MEMBERSHIP_ID
        )).thenReturn(List.of("farm-001"));
    }
}
