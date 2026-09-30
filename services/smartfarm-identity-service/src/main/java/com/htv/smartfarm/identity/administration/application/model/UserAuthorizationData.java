package com.htv.smartfarm.identity.administration.application.model;

import java.time.Instant;
import java.util.List;

import com.htv.smartfarm.identity.authorization.application.model.PermissionData;
import com.htv.smartfarm.identity.tenant.domain.MembershipStatus;

public record UserAuthorizationData(
        String subjectId,
        String membershipId,
        String tenantId,
        AccountStatusData accountStatus,
        MembershipStatus membershipStatus,
        List<RoleData> roles,
        List<PermissionData> effectivePermissions,
        List<String> farmIds,
        Instant membershipUpdatedAt
) {

    public UserAuthorizationData {
        roles = roles == null
                ? List.of()
                : List.copyOf(roles);

        effectivePermissions = effectivePermissions == null
                ? List.of()
                : List.copyOf(effectivePermissions);

        farmIds = farmIds == null
                ? List.of()
                : List.copyOf(farmIds);
    }
}