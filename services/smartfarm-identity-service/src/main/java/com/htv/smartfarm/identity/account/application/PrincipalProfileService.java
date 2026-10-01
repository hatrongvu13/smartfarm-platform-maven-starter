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
            "display_name", "first_name", "last_name", "phone_number",
            "avatar_url", "locale", "time_zone"
    );

    private final UserProfileRepository userProfileRepository;
    private final TenantMembershipRepository membershipRepository;
    private final MembershipRoleRepository membershipRoleRepository;
    private final MembershipFarmRepository membershipFarmRepository;

    public PrincipalProfileService(UserProfileRepository userProfileRepository,
            TenantMembershipRepository membershipRepository,
            MembershipRoleRepository membershipRoleRepository,
            MembershipFarmRepository membershipFarmRepository) {
        this.userProfileRepository = userProfileRepository;
        this.membershipRepository = membershipRepository;
        this.membershipRoleRepository = membershipRoleRepository;
        this.membershipFarmRepository = membershipFarmRepository;
    }

    @Transactional(readOnly = true)
    public PrincipalData getPrincipal(String tenantId, String subjectId) {
        requireText(tenantId, "tenantId");
        requireText(subjectId, "subjectId");
        UserProfileEntity profile = profile(subjectId);
        TenantMembershipEntity membership = membership(tenantId, subjectId);
        return toPrincipalData(profile, membership);
    }

    @Transactional
    public PrincipalData updateProfile(String tenantId, UpdateProfileCommand command) {
        requireText(tenantId, "tenantId");
        validateUpdateCommand(command);
        UserProfileEntity profile = profile(command.subjectId());
        if (profile.getVersion() != command.expectedVersion()) {
            throw new OptimisticConflictException(command.expectedVersion(), profile.getVersion());
        }
        Set<String> fields = command.updateFields();
        profile.update(
                value(fields, "display_name", command.displayName(), profile.getDisplayName(), true, "displayName"),
                value(fields, "first_name", command.firstName(), profile.getFirstName(), false, "firstName"),
                value(fields, "last_name", command.lastName(), profile.getLastName(), false, "lastName"),
                value(fields, "phone_number", command.phoneNumber(), profile.getPhoneNumber(), false, "phoneNumber"),
                value(fields, "avatar_url", command.avatarUrl(), profile.getAvatarUrl(), false, "avatarUrl"),
                value(fields, "locale", command.locale(), profile.getLocale(), true, "locale"),
                value(fields, "time_zone", command.timeZone(), profile.getTimeZone(), true, "timeZone")
        );
        userProfileRepository.flush();
        return toPrincipalData(profile, membership(tenantId, command.subjectId()));
    }

    private String value(Set<String> fields, String path, String requested,
            String current, boolean required, String field) {
        if (!fields.contains(path)) return current;
        return required ? requireText(requested, field).trim() : normalizeNullable(requested);
    }

    private UserProfileEntity profile(String subjectId) {
        return userProfileRepository.findWithAccountByUserId(subjectId)
                .orElseThrow(() -> NotFoundException.entity("UserProfile", subjectId));
    }

    private TenantMembershipEntity membership(String tenantId, String subjectId) {
        return membershipRepository.findByTenantIdAndUserId(tenantId, subjectId)
                .orElseThrow(() -> NotFoundException.entity("TenantMembership", tenantId + ":" + subjectId));
    }

    private PrincipalData toPrincipalData(UserProfileEntity profile,
            TenantMembershipEntity membership) {
        var account = profile.getAccount();
        PrincipalProfileData profileData = new PrincipalProfileData(
                profile.getDisplayName(), account.getEmail(), profile.getFirstName(),
                profile.getLastName(), profile.getPhoneNumber(), profile.getAvatarUrl(),
                profile.getLocale(), profile.getTimeZone(), account.isEmailVerified(),
                account.isPhoneVerified());
        TenantMembershipData membershipData = new TenantMembershipData(
                membership.getId(), membership.getTenant().getId(), membership.getStatus(),
                membershipRoleRepository.findRoleCodesByMembershipId(membership.getId()),
                membershipFarmRepository.findFarmIdsByMembershipId(membership.getId()));
        return new PrincipalData(account.getId(), profileData, account.isEnabled(), membershipData,
                profile.getCreatedAt(), profile.getUpdatedAt(), profile.getVersion());
    }

    private void validateUpdateCommand(UpdateProfileCommand command) {
        if (command == null) throw new ValidationException(
                "UPDATE_PROFILE_COMMAND_REQUIRED", "Update profile command must not be null");
        requireText(command.subjectId(), "subjectId");
        if (command.expectedVersion() < 0) throw new ValidationException(
                "EXPECTED_VERSION_INVALID", "expectedVersion must not be negative");
        if (command.updateFields().isEmpty()) throw new ValidationException(
                "UPDATE_FIELDS_REQUIRED", "Update fields must not be empty");
        if (!ALLOWED_PROFILE_FIELDS.containsAll(command.updateFields())) throw new ValidationException(
                "UPDATE_FIELDS_UNSUPPORTED", "Update contains unsupported profile fields");
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new ValidationException(
                "REQUIRED_FIELD_MISSING", field + " must not be blank");
        return value;
    }
}
