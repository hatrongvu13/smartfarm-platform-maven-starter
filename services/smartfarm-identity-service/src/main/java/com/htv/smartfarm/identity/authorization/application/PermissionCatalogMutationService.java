package com.htv.smartfarm.identity.authorization.application;

import com.htv.smartfarm.common.exception.ConflictException;
import com.htv.smartfarm.common.exception.NotFoundException;
import com.htv.smartfarm.identity.authorization.domain.PermissionCode;
import com.htv.smartfarm.identity.authorization.domain.PermissionEntity;
import com.htv.smartfarm.identity.authorization.repository.PermissionRepository;
import com.htv.smartfarm.identity.authorization.repository.RolePermissionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single mutation boundary for the global permission catalog.
 * Runtime mutations always identify the tenant whose effective policy changes.
 */
@Service
public class PermissionCatalogMutationService {
    private final PermissionRepository permissions;
    private final RolePermissionRepository grants;
    private final TenantPermissionCatalogAdministrationService tenantMutations;

    public PermissionCatalogMutationService(
            PermissionRepository permissions,
            RolePermissionRepository grants,
            TenantPermissionCatalogAdministrationService tenantMutations
    ) {
        this.permissions = permissions;
        this.grants = grants;
        this.tenantMutations = tenantMutations;
    }

    @Transactional
    public PermissionEntity create(
            String tenantId,
            String code,
            String resourceType,
            String action,
            String description,
            String actorId,
            String correlationId
    ) {
        return tenantMutations.apply(
                tenantId,
                actorId,
                correlationId,
                "PERMISSION_CATALOG_CREATED",
                () -> createInternal(code, resourceType, action, description)
        );
    }

    @Transactional
    public PermissionEntity update(
            String tenantId,
            String code,
            String resourceType,
            String action,
            String description,
            String actorId,
            String correlationId
    ) {
        return tenantMutations.apply(
                tenantId,
                actorId,
                correlationId,
                "PERMISSION_CATALOG_UPDATED",
                () -> updateInternal(code, resourceType, action, description)
        );
    }

    @Transactional
    public boolean delete(
            String tenantId,
            String code,
            String actorId,
            String correlationId
    ) {
        return tenantMutations.apply(
                tenantId,
                actorId,
                correlationId,
                "PERMISSION_CATALOG_DELETED",
                () -> deleteInternal(code)
        );
    }

    PermissionEntity createInternal(
            String code,
            String resourceType,
            String action,
            String description
    ) {
        String normalizedCode = PermissionCode.normalize(code);
        guardProtected(normalizedCode);
        String normalizedResource = normalizePart(resourceType, "resourceType");
        String normalizedAction = normalizePart(action, "action");
        requireCodeMatchesMetadata(normalizedCode, normalizedResource, normalizedAction);

        if (permissions.existsById(normalizedCode)) {
            throw new ConflictException(
                    "PERMISSION_CODE_ALREADY_EXISTS",
                    "Permission code already exists"
            );
        }
        if (permissions.existsByResourceTypeAndAction(normalizedResource, normalizedAction)) {
            throw new ConflictException(
                    "PERMISSION_ALREADY_EXISTS",
                    "Permission already exists for resource and action"
            );
        }
        return permissions.save(new PermissionEntity(
                normalizedCode,
                normalizedResource,
                normalizedAction,
                clean(description)
        ));
    }

    PermissionEntity updateInternal(
            String code,
            String resourceType,
            String action,
            String description
    ) {
        String normalizedCode = PermissionCode.normalize(code);
        guardProtected(normalizedCode);
        String normalizedResource = normalizePart(resourceType, "resourceType");
        String normalizedAction = normalizePart(action, "action");
        requireCodeMatchesMetadata(normalizedCode, normalizedResource, normalizedAction);

        PermissionEntity permission = permissions.findById(normalizedCode)
                .orElseThrow(() -> NotFoundException.entity(
                        "Permission",
                        normalizedCode
                ));
        permissions.findByResourceTypeAndAction(normalizedResource, normalizedAction)
                .filter(value -> !value.getCode().equals(normalizedCode))
                .ifPresent(value -> {
                    throw new ConflictException(
                            "PERMISSION_ALREADY_EXISTS",
                            "Permission already exists for resource and action"
                    );
                });
        permission.updateMetadata(normalizedResource, normalizedAction, description);
        return permission;
    }

    boolean deleteInternal(String code) {
        String normalizedCode = PermissionCode.normalize(code);
        guardProtected(normalizedCode);
        if (!permissions.existsById(normalizedCode)) return false;
        if (grants.countByIdPermissionCode(normalizedCode) > 0) {
            throw new ConflictException(
                    "PERMISSION_IN_USE",
                    "Revoke permission from all roles before deleting it"
            );
        }
        permissions.deleteById(normalizedCode);
        return true;
    }

    private static void requireCodeMatchesMetadata(
            String code,
            String resourceType,
            String action
    ) {
        if (!PermissionCode.resourceType(code).equals(resourceType)
                || !PermissionCode.action(code).equals(action)) {
            throw new IllegalArgumentException(
                    "permission code must match resourceType and action"
            );
        }
    }

    private static void guardProtected(String code) {
        if ("*".equals(code)) {
            throw new ConflictException(
                    "PROTECTED_PERMISSION",
                    "Wildcard permission is protected"
            );
        }
    }

    private static String normalizePart(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
