package com.htv.smartfarm.identity.grpc;

import java.time.Instant;

import com.google.protobuf.Timestamp;

import com.htv.smartfarm.identity.administration.application.model.AccountStatusData;
import com.htv.smartfarm.identity.administration.application.model.CreatedTenantUser;
import com.htv.smartfarm.identity.administration.application.model.RoleData;
import com.htv.smartfarm.identity.administration.application.model.RoleTypeData;
import com.htv.smartfarm.identity.administration.application.model.UserAuthorizationData;
import com.htv.smartfarm.identity.administration.application.model.UserSummaryData;
import com.htv.smartfarm.identity.authorization.application.model.PermissionData;
import com.htv.smartfarm.common.paging.PageResult;
import com.htv.smartfarm.identity.tenant.domain.MembershipStatus;
import com.htv.smartfarm.proto.common.v1.PageResponse;
import com.htv.smartfarm.proto.identity.v1.AccountStatus;
import com.htv.smartfarm.proto.identity.v1.AssignRoleResponse;
import com.htv.smartfarm.proto.identity.v1.CreateUserResponse;
import com.htv.smartfarm.proto.identity.v1.ListPermissionsResponse;
import com.htv.smartfarm.proto.identity.v1.ListPlatformRolesResponse;
import com.htv.smartfarm.proto.identity.v1.ListRolesResponse;
import com.htv.smartfarm.proto.identity.v1.ListUsersResponse;
import com.htv.smartfarm.proto.identity.v1.PermissionInfo;
import com.htv.smartfarm.proto.identity.v1.RevokeRoleResponse;
import com.htv.smartfarm.proto.identity.v1.RoleInfo;
import com.htv.smartfarm.proto.identity.v1.RoleType;
import com.htv.smartfarm.proto.identity.v1.UpdateMembershipStatusResponse;
import com.htv.smartfarm.proto.identity.v1.UserAuthorization;
import com.htv.smartfarm.proto.identity.v1.UserSummary;

import org.springframework.stereotype.Component;

@Component
public class IdentityAdministrationProtoMapper {

    public RoleInfo toProto(RoleData source) {
        requireSource(source, "role");

        RoleInfo.Builder result = RoleInfo.newBuilder()
                .setRoleId(safe(source.roleId()))
                .setTenantId(safe(source.tenantId()))
                .setCode(safe(source.code()))
                .setName(safe(source.name()))
                .setType(toProtoRoleType(source.type()))
                .setVersion(source.version())
                .addAllPermissions(
                        source.permissions()
                                .stream()
                                .map(this::toProto)
                                .toList()
                );

        setTimestampIfPresent(
                source.createdAt(),
                result::setCreatedAt
        );

        setTimestampIfPresent(
                source.updatedAt(),
                result::setUpdatedAt
        );

        return result.build();
    }

    public PermissionInfo toProto(PermissionData source) {
        requireSource(source, "permission");

        return PermissionInfo.newBuilder()
                .setCode(safe(source.code()))
                .setResourceType(safe(source.resourceType()))
                .setAction(safe(source.action()))
                .setDescription(safe(source.description()))
                .build();
    }

    public UserSummary toProto(UserSummaryData source) {
        requireSource(source, "userSummary");

        UserSummary.Builder result = UserSummary.newBuilder()
                .setSubjectId(safe(source.subjectId()))
                .setMembershipId(safe(source.membershipId()))
                .setTenantId(safe(source.tenantId()))
                .setEmail(safe(source.email()))
                .setDisplayName(safe(source.displayName()))
                .setAccountStatus(
                        toProtoAccountStatus(
                                source.accountStatus()
                        )
                )
                .setMembershipStatus(
                        toProtoMembershipStatus(
                                source.membershipStatus()
                        )
                )
                .addAllRoles(source.roles());

        setTimestampIfPresent(
                source.createdAt(),
                result::setCreatedAt
        );

        setTimestampIfPresent(
                source.updatedAt(),
                result::setUpdatedAt
        );

        return result.build();
    }

    public UserAuthorization toProto(
            UserAuthorizationData source
    ) {
        requireSource(source, "userAuthorization");

        return UserAuthorization.newBuilder()
                .setSubjectId(safe(source.subjectId()))
                .setMembershipId(safe(source.membershipId()))
                .setTenantId(safe(source.tenantId()))
                .setAccountStatus(
                        toProtoAccountStatus(
                                source.accountStatus()
                        )
                )
                .setMembershipStatus(
                        toProtoMembershipStatus(
                                source.membershipStatus()
                        )
                )
                .addAllRoles(
                        source.roles()
                                .stream()
                                .map(this::toProto)
                                .toList()
                )
                .addAllEffectivePermissions(
                        source.effectivePermissions()
                                .stream()
                                .map(this::toProto)
                                .toList()
                )
                .addAllFarmIds(source.farmIds())
                .build();
    }

    public ListRolesResponse toListRolesResponse(
            PageResult<RoleData> page
    ) {
        requirePage(page);

        return ListRolesResponse.newBuilder()
                .addAllRoles(
                        page.items()
                                .stream()
                                .map(this::toProto)
                                .toList()
                )
                .setPage(toPageResponse(page))
                .build();
    }

    public ListPlatformRolesResponse
    toListPlatformRolesResponse(
            PageResult<RoleData> page
    ) {
        requirePage(page);

        return ListPlatformRolesResponse.newBuilder()
                .addAllRoles(
                        page.items()
                                .stream()
                                .map(this::toProto)
                                .toList()
                )
                .setPage(toPageResponse(page))
                .build();
    }

    public ListPermissionsResponse toListPermissionsResponse(
            PageResult<PermissionData> page
    ) {
        requirePage(page);

        return ListPermissionsResponse.newBuilder()
                .addAllPermissions(
                        page.items()
                                .stream()
                                .map(this::toProto)
                                .toList()
                )
                .setPage(toPageResponse(page))
                .build();
    }

    public ListUsersResponse toListUsersResponse(
            PageResult<UserSummaryData> page
    ) {
        requirePage(page);

        return ListUsersResponse.newBuilder()
                .addAllUsers(
                        page.items()
                                .stream()
                                .map(this::toProto)
                                .toList()
                )
                .setPage(toPageResponse(page))
                .build();
    }

    public CreateUserResponse toCreateUserResponse(
            CreatedTenantUser source
    ) {
        requireSource(source, "createdTenantUser");

        return CreateUserResponse.newBuilder()
                .setSubjectId(safe(source.subjectId()))
                .setMembershipId(safe(source.membershipId()))
                .setTenantId(safe(source.tenantId()))
                .setMembershipStatus(
                        toProtoMembershipStatus(
                                source.membershipStatus()
                        )
                )
                .setExistingAccount(
                        source.existingAccount()
                )
                .build();
    }

    public AssignRoleResponse toAssignRoleResponse(
            UserAuthorizationData source
    ) {
        requireSource(source, "userAuthorization");

        return AssignRoleResponse.newBuilder()
                .setSubjectId(safe(source.subjectId()))
                .setMembershipId(safe(source.membershipId()))
                .setTenantId(safe(source.tenantId()))
                .addAllRoleCodes(
                        source.roles()
                                .stream()
                                .map(RoleData::code)
                                .filter(this::hasText)
                                .distinct()
                                .sorted()
                                .toList()
                )
                .build();
    }

    public RevokeRoleResponse toRevokeRoleResponse(
            UserAuthorizationData source
    ) {
        requireSource(source, "userAuthorization");

        return RevokeRoleResponse.newBuilder()
                .setSubjectId(safe(source.subjectId()))
                .setMembershipId(safe(source.membershipId()))
                .setTenantId(safe(source.tenantId()))
                .addAllRoleCodes(
                        source.roles()
                                .stream()
                                .map(RoleData::code)
                                .filter(this::hasText)
                                .distinct()
                                .sorted()
                                .toList()
                )
                .build();
    }

    public UpdateMembershipStatusResponse
    toMembershipStatusResponse(
            UserAuthorizationData source
    ) {
        requireSource(source, "userAuthorization");

        UpdateMembershipStatusResponse.Builder result =
                UpdateMembershipStatusResponse.newBuilder()
                        .setSubjectId(safe(source.subjectId()))
                        .setMembershipId(
                                safe(source.membershipId())
                        )
                        .setTenantId(safe(source.tenantId()))
                        .setStatus(
                                toProtoMembershipStatus(
                                        source.membershipStatus()
                                )
                        );

        if (source.membershipUpdatedAt() != null) {
            result.setUpdatedAt(
                    toTimestamp(
                            source.membershipUpdatedAt()
                    )
            );
        }

        return result.build();
    }

    public MembershipStatus toDomainStatus(
            com.htv.smartfarm.proto.identity.v1
                    .MembershipStatus source
    ) {
        if (source == null) {
            return null;
        }

        return switch (source) {
            case MEMBERSHIP_STATUS_INVITED ->
                    MembershipStatus.INVITED;

            case MEMBERSHIP_STATUS_ACTIVE ->
                    MembershipStatus.ACTIVE;

            case MEMBERSHIP_STATUS_SUSPENDED ->
                    MembershipStatus.SUSPENDED;

            case MEMBERSHIP_STATUS_DISABLED ->
                    MembershipStatus.DISABLED;

            case MEMBERSHIP_STATUS_UNSPECIFIED,
                 UNRECOGNIZED -> null;
        };
    }

    private com.htv.smartfarm.proto.identity.v1
            .MembershipStatus toProtoMembershipStatus(
            MembershipStatus source
    ) {
        if (source == null) {
            return com.htv.smartfarm.proto.identity.v1
                    .MembershipStatus
                    .MEMBERSHIP_STATUS_UNSPECIFIED;
        }

        return switch (source) {
            case INVITED ->
                    com.htv.smartfarm.proto.identity.v1
                            .MembershipStatus
                            .MEMBERSHIP_STATUS_INVITED;

            case ACTIVE ->
                    com.htv.smartfarm.proto.identity.v1
                            .MembershipStatus
                            .MEMBERSHIP_STATUS_ACTIVE;

            case SUSPENDED ->
                    com.htv.smartfarm.proto.identity.v1
                            .MembershipStatus
                            .MEMBERSHIP_STATUS_SUSPENDED;

            case DISABLED ->
                    com.htv.smartfarm.proto.identity.v1
                            .MembershipStatus
                            .MEMBERSHIP_STATUS_DISABLED;
        };
    }

    private AccountStatus toProtoAccountStatus(
            AccountStatusData source
    ) {
        if (source == null) {
            return AccountStatus
                    .ACCOUNT_STATUS_UNSPECIFIED;
        }

        return switch (source) {
            case ACTIVE ->
                    AccountStatus.ACCOUNT_STATUS_ACTIVE;

            case LOCKED ->
                    AccountStatus.ACCOUNT_STATUS_LOCKED;

            case DISABLED ->
                    AccountStatus.ACCOUNT_STATUS_DISABLED;
        };
    }

    private RoleType toProtoRoleType(
            RoleTypeData source
    ) {
        if (source == null) {
            return RoleType.ROLE_TYPE_UNSPECIFIED;
        }

        return switch (source) {
            case TENANT ->
                    RoleType.ROLE_TYPE_TENANT;

            case SYSTEM ->
                    RoleType.ROLE_TYPE_SYSTEM;

            case PLATFORM ->
                    RoleType.ROLE_TYPE_PLATFORM;
        };
    }

    private PageResponse toPageResponse(
            PageResult<?> page
    ) {
        return PageResponse.newBuilder()
                .setNextPageToken(
                        safe(page.nextPageToken())
                )
                .build();
    }

    private Timestamp toTimestamp(Instant source) {
        return Timestamp.newBuilder()
                .setSeconds(source.getEpochSecond())
                .setNanos(source.getNano())
                .build();
    }

    private void setTimestampIfPresent(
            Instant source,
            java.util.function.Consumer<Timestamp> setter
    ) {
        if (source != null) {
            setter.accept(toTimestamp(source));
        }
    }

    private void requirePage(PageResult<?> page) {
        if (page == null) {
            throw new IllegalArgumentException(
                    "page result must not be null"
            );
        }
    }

    private void requireSource(
            Object source,
            String objectName
    ) {
        if (source == null) {
            throw new IllegalArgumentException(
                    objectName + " must not be null"
            );
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}