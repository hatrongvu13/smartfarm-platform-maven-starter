package com.htv.smartfarm.identity.authorization.application;

import com.htv.smartfarm.identity.authorization.application.model.PermissionData;
import com.htv.smartfarm.common.paging.PageResult;

public interface AuthorizationCatalogService {

    PageResult<PermissionData> listPermissions(
            int pageSize,
            String pageToken,
            String resourceType
    );
}