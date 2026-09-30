package com.htv.smartfarm.identity.account.application;

import java.util.Set;

import com.htv.smartfarm.common.exception.NotFoundException;
import com.htv.smartfarm.common.exception.ValidationException;
import com.htv.smartfarm.identity.account.application.command.UpdateProfileCommand;
import com.htv.smartfarm.identity.account.application.model.PrincipalData;
import com.htv.smartfarm.identity.account.application.model.PrincipalProfileData;
import com.htv.smartfarm.identity.account.domain.UserProfileEntity;
import com.htv.smartfarm.identity.account.repository.UserProfileRepository;
import com.htv.smartfarm.identity.authorization.repository.MembershipFarmRepository;
import com.htv.smartfarm.identity.authorization.repository.MembershipRoleRepository;
import com.htv.smartfarm.identity.shared.exception.OptimisticConflictException;
import com.htv.smartfarm.identity.tenant.application.model.TenantMembershipData;
import com.htv.smartfarm.identity.tenant.domain.TenantMembershipEntity;
import com.htv.smartfarm.identity.tenant.repository.TenantMembershipRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrincipalProfileService {

    private static final Set<String> ALLOWED_PROFILE_FIELDS = Set.of(
            "display_name",
            "phone_number",
            "locale",
            "time_zone"
    );

    private final UserProfileRepository userProfileRepository;
    private final TenantMembershipRepository membershipRepository;
    private final MembershipRoleRepository membershipRoleRepository;
    private final MembershipFarmRepository membershipFarmRepository;

    public PrincipalProfileService(
            UserProfileRepository userProfileRepository,
            TenantMembershipRepository membershipRepository,
            MembershipRoleRepository membershipRoleRepository,
            MembershipFarmRepository membershipFarmRepository
    ) {
        this.userProfileRepository = userProfileRepository;
        this.membershipRepository = membershipRepository;
        this.membershipRoleRepository = membershipRoleRepository;
        this.membershipFarmRepository = membershipFarmRepository;
    }

    @Transactional(readOnly = true)
    public PrincipalData getPrincipal(
            String tenantId,
            String subjectId
    ) {
        requireText(tenantId, "tenantId");
        requireText(subjectId, "subjectId");

        UserProfileEntity profile = userProfileRepository
                .findWithAccountByUserId(subjectId)
                .orElseThrow(() -> NotFoundException.entity(
                        "UserProfile",
                        subjectId
                ));

        TenantMembershipEntity membership = membershipRepository
                .findByTenantIdAndUserId(tenantId, subjectId)
                .orElseThrow(() -> NotFoundException.entity(
                        "TenantMembership",
                        tenantId + ":" + subjectId
                ));

        return toPrincipalData(profile, membership);
    }

    @Transactional
    public PrincipalData updateProfile(
            String tenantId,
            UpdateProfileCommand command
    ) {
        requireText(tenantId, "tenantId");
        validateUpdateCommand(command);

        UserProfileEntity profile = userProfileRepository
                .findWithAccountByUserId(command.subjectId())
                .orElseThrow(() -> NotFoundException.entity(
                        "UserProfile",
                        command.subjectId()
                ));

        if (profile.getVersion() != command.expectedVersion()) {
            throw new OptimisticConflictException(
                    command.expectedVersion(),
                    profile.getVersion()
            );
        }

        Set<String> fields = command.updateFields();

        String displayName = fields.contains("display_name")
                ? requireText(command.displayName(), "displayName").trim()
                : profile.getDisplayName();

        String phoneNumber = fields.contains("phone_number")
                ? normalizeNullable(command.phoneNumber())
                : profile.getPhoneNumber();

        String locale = fields.contains("locale")
                ? requireText(command.locale(), "locale").trim()
                : profile.getLocale();

        String timeZone = fields.contains("time_zone")
                ? requireText(command.timeZone(), "timeZone").trim()
                : profile.getTimeZone();

        profile.update(displayName, phoneNumber, locale, timeZone);
        userProfileRepository.flush();

        TenantMembershipEntity membership = membershipRepository
                .findByTenantIdAndUserId(tenantId, command.subjectId())
                .orElseThrow(() -> NotFoundException.entity(
                        "TenantMembership",
                        tenantId + ":" + command.subjectId()
                ));

        return toPrincipalData(profile, membership);
    }

    private PrincipalData toPrincipalData(
            UserProfileEntity profile,
            TenantMembershipEntity membership
    ) {
        var roleCodes = membershipRoleRepository
                .findRoleCodesByMembershipId(membership.getId());

        var farmIds = membershipFarmRepository
                .findFarmIdsByMembershipId(membership.getId());

        PrincipalProfileData profileData = new PrincipalProfileData(
                profile.getDisplayName(),
                profile.getAccount().getEmail(),
                profile.getPhoneNumber(),
                profile.getLocale(),
                profile.getTimeZone()
        );

        TenantMembershipData membershipData = new TenantMembershipData(
                membership.getId(),
                membership.getTenant().getId(),
                membership.getStatus(),
                roleCodes,
                farmIds
        );

        return new PrincipalData(
                profile.getAccount().getId(),
                profileData,
                profile.getAccount().isEnabled(),
                membershipData,
                profile.getCreatedAt(),
                profile.getUpdatedAt(),
                profile.getVersion()
        );
    }

    private void validateUpdateCommand(UpdateProfileCommand command) {
        if (command == null) {
            throw new ValidationException(
                    "UPDATE_PROFILE_COMMAND_REQUIRED",
                    "Update profile command must not be null"
            );
        }

        requireText(command.subjectId(), "subjectId");

        if (command.expectedVersion() < 0) {
            throw new ValidationException(
                    "EXPECTED_VERSION_INVALID",
                    "expectedVersion must not be negative"
            );
        }

        if (command.updateFields() == null
                || command.updateFields().isEmpty()) {
            throw new ValidationException(
                    "UPDATE_FIELDS_REQUIRED",
                    "Update fields must not be empty"
            );
        }

        if (!ALLOWED_PROFILE_FIELDS.containsAll(command.updateFields())) {
            throw new ValidationException(
                    "UPDATE_FIELDS_UNSUPPORTED",
                    "Update contains unsupported profile fields"
            );
        }
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(
                    "REQUIRED_FIELD_MISSING",
                    field + " must not be blank"
            );
        }

        return value;
    }
}
