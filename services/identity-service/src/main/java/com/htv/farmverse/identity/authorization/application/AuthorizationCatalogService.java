package com.htv.farmverse.identity.authorization.application;

import com.htv.farmverse.identity.authorization.application.model.PermissionData;
import com.htv.farmverse.common.paging.PageResult;

public interface AuthorizationCatalogService {

    PermissionData getPermission(String code);

    PageResult<PermissionData> listPermissions(
            int pageSize,
            String pageToken,
            String resourceType
    );
}