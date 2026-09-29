package com.htv.smartfarm.identity.grpc;

import com.google.protobuf.FieldMask;
import com.google.protobuf.Timestamp;

import com.htv.smartfarm.identity.account.application.command
        .UpdateProfileCommand;
import com.htv.smartfarm.identity.account.application.model
        .PrincipalData;
import com.htv.smartfarm.identity.authorization.application.model
        .PermissionDecision;
import com.htv.smartfarm.proto.identity.v1.CheckPermissionResponse;
import com.htv.smartfarm.proto.identity.v1.MembershipStatus;
import com.htv.smartfarm.proto.identity.v1.PermissionTarget;
import com.htv.smartfarm.proto.identity.v1.Principal;
import com.htv.smartfarm.proto.identity.v1.PrincipalProfile;
import com.htv.smartfarm.proto.identity.v1.TenantMembership;
import com.htv.smartfarm.proto.identity.v1
        .UpdatePrincipalProfileRequest;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class IdentityProtoMapper {

    public Principal toProto(PrincipalData source) {
        Principal.Builder result = Principal.newBuilder()
                .setSubjectId(source.subjectId())
                .setActive(source.active())
                .setVersion(source.version())
                .setProfile(toProfileProto(source))
                .setMembership(toMembershipProto(source));

        if (source.createdAt() != null) {
            result.setCreatedAt(
                    toTimestamp(source.createdAt())
            );
        }

        if (source.updatedAt() != null) {
            result.setUpdatedAt(
                    toTimestamp(source.updatedAt())
            );
        }

        return result.build();
    }

    public UpdateProfileCommand toUpdateProfileCommand(
            String subjectId,
            UpdatePrincipalProfileRequest request
    ) {
        PrincipalProfile profile = request.getProfile();

        return new UpdateProfileCommand(
                subjectId,
                profile.getDisplayName(),
                profile.getPhoneNumber(),
                profile.getLocale(),
                profile.getTimeZone(),
                toUpdateFields(request.getUpdateMask()),
                request.getExpectedVersion()
        );
    }

    public com.htv.smartfarm.identity.authorization.application
            .model.PermissionTarget toApplicationTarget(
            PermissionTarget source
    ) {
        return new com.htv.smartfarm.identity.authorization.application
                .model.PermissionTarget(
                normalizeRequired(
                        source.getResourceType(),
                        "resource_type"
                ),
                normalizeNullable(source.getResourceId()),
                normalizeRequired(
                        source.getAction(),
                        "action"
                )
        );
    }

    public List<com.htv.smartfarm.identity.authorization.application
            .model.PermissionTarget> toApplicationTargets(
            List<PermissionTarget> sources
    ) {
        return sources.stream()
                .map(this::toApplicationTarget)
                .toList();
    }

    public CheckPermissionResponse toProto(
            PermissionDecision source
    ) {
        return CheckPermissionResponse.newBuilder()
                .setAllowed(source.allowed())
                .setReasonCode(source.reasonCode())
                .build();
    }

    public com.htv.smartfarm.proto.identity.v1.PermissionDecision
    toPermissionDecisionProto(
            PermissionDecision source
    ) {
        return com.htv.smartfarm.proto.identity.v1
                .PermissionDecision
                .newBuilder()
                .setTarget(
                        toPermissionTargetProto(
                                source.target()
                        )
                )
                .setAllowed(source.allowed())
                .setReasonCode(source.reasonCode())
                .build();
    }

    private PrincipalProfile toProfileProto(
            PrincipalData source
    ) {
        return PrincipalProfile.newBuilder()
                .setDisplayName(
                        safe(source.profile().displayName())
                )
                .setEmail(
                        safe(source.profile().email())
                )
                .setPhoneNumber(
                        safe(source.profile().phoneNumber())
                )
                .setLocale(
                        safe(source.profile().locale())
                )
                .setTimeZone(
                        safe(source.profile().timeZone())
                )
                .build();
    }

    private TenantMembership toMembershipProto(
            PrincipalData source
    ) {
        return TenantMembership.newBuilder()
                .setTenantId(
                        source.membership().tenantId()
                )
                .setStatus(
                        toProtoStatus(
                                source.membership().status()
                        )
                )
                .addAllRoles(
                        source.membership().roles()
                )
                .addAllFarmIds(
                        source.membership().farmIds()
                )
                .build();
    }

    private MembershipStatus toProtoStatus(
            com.htv.smartfarm.identity.tenant.domain
                    .MembershipStatus source
    ) {
        if (source == null) {
            return MembershipStatus
                    .MEMBERSHIP_STATUS_UNSPECIFIED;
        }

        return switch (source) {
            case INVITED -> MembershipStatus
                    .MEMBERSHIP_STATUS_INVITED;

            case ACTIVE -> MembershipStatus
                    .MEMBERSHIP_STATUS_ACTIVE;

            case SUSPENDED -> MembershipStatus
                    .MEMBERSHIP_STATUS_SUSPENDED;

            case DISABLED -> MembershipStatus
                    .MEMBERSHIP_STATUS_DISABLED;
        };
    }

    private PermissionTarget toPermissionTargetProto(
            com.htv.smartfarm.identity.authorization.application
                    .model.PermissionTarget source
    ) {
        PermissionTarget.Builder result =
                PermissionTarget.newBuilder()
                        .setResourceType(
                                safe(source.resourceType())
                        )
                        .setAction(
                                safe(source.action())
                        );

        if (source.resourceId() != null) {
            result.setResourceId(source.resourceId());
        }

        return result.build();
    }

    private Set<String> toUpdateFields(
            FieldMask fieldMask
    ) {
        if (fieldMask == null
                || fieldMask.getPathsCount() == 0) {
            throw new IllegalArgumentException(
                    "update_mask must not be empty"
            );
        }

        return fieldMask.getPathsList()
                .stream()
                .map(this::normalizeFieldPath)
                .collect(Collectors.toUnmodifiableSet());
    }

    private String normalizeFieldPath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException(
                    "update_mask contains an empty path"
            );
        }

        return switch (path.trim()) {
            case "display_name",
                 "profile.display_name" -> "display_name";

            case "phone_number",
                 "profile.phone_number" -> "phone_number";

            case "locale",
                 "profile.locale" -> "locale";

            case "time_zone",
                 "profile.time_zone" -> "time_zone";

            default -> throw new IllegalArgumentException(
                    "Unsupported update path: " + path
            );
        };
    }

    private Timestamp toTimestamp(Instant source) {
        return Timestamp.newBuilder()
                .setSeconds(source.getEpochSecond())
                .setNanos(source.getNano())
                .build();
    }

    private String normalizeRequired(
            String value,
            String field
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank"
            );
        }

        return value.trim().toUpperCase();
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}