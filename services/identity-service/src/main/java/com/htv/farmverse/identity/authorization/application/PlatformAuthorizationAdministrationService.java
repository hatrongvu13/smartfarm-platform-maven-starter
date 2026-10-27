package com.htv.farmverse.identity.authorization.application;

import java.util.List;

import com.htv.farmverse.identity.administration.application.model.RoleData;
import com.htv.farmverse.common.paging.PageResult;

public interface PlatformAuthorizationAdministrationService {

    PageResult<RoleData> listPlatformRoles(
            int pageSize,
            String pageToken,
            boolean includePermissions
    );

    RoleData createPlatformRole(
            String roleCode,
            String roleName,
            String description,
            List<String> permissionCodes,
            String actorId,
            String correlationId
    );

    RoleData grantPermission(
            String roleCode,
            String permissionCode,
            String actorId
    );

    RoleData revokePermission(
            String roleCode,
            String permissionCode,
            String actorId
    );
}