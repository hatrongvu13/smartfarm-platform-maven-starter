package com.htv.smartfarm.identity.authorization.application;

import java.util.Comparator;
import java.util.List;

import com.htv.smartfarm.common.paging.PageResult;
import com.htv.smartfarm.identity.authorization.application.model.PermissionData;
import com.htv.smartfarm.identity.authorization.domain.PermissionEntity;
import com.htv.smartfarm.identity.authorization.repository.PermissionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DefaultAuthorizationCatalogService
        implements AuthorizationCatalogService {

    private final PermissionRepository permissionRepository;

    public DefaultAuthorizationCatalogService(
            PermissionRepository permissionRepository
    ) {
        this.permissionRepository = permissionRepository;
    }

    @Override
    public PageResult<PermissionData> listPermissions(
            int pageSize,
            String pageToken,
            String resourceType
    ) {
        List<PermissionEntity> permissions = resourceType == null
                || resourceType.isBlank()
                ? permissionRepository.findAll()
                : permissionRepository.findAllByResourceTypeOrderByActionAsc(
                        resourceType.trim().toUpperCase()
                );

        List<PermissionData> data = permissions.stream()
                .sorted(Comparator.comparing(PermissionEntity::getCode))
                .map(this::toData)
                .toList();

        return PageSupport.page(data, pageSize, pageToken);
    }

    private PermissionData toData(PermissionEntity permission) {
        return new PermissionData(
                permission.getCode(),
                permission.getResourceType(),
                permission.getAction(),
                permission.getDescription()
        );
    }
}
