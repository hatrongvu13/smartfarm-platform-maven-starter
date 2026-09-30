package com.htv.smartfarm.identity.authorization.application;

import java.util.List;

import com.htv.smartfarm.identity.administration.application.model.RoleData;
import com.htv.smartfarm.common.paging.PageResult;

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
            String actorId
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