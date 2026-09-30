package com.htv.smartfarm.identity.administration.application.model;

import com.htv.smartfarm.identity.authorization.application.model.PermissionData;

import java.time.Instant;
import java.util.List;

public record RoleData(
        String roleId,
        String tenantId,
        String code,
        String name,
        RoleTypeData type,
        List<PermissionData> permissions,
        Instant createdAt,
        Instant updatedAt,
        long version
) {

    public RoleData {
        permissions = permissions == null
                ? List.of()
                : List.copyOf(permissions);
    }
}