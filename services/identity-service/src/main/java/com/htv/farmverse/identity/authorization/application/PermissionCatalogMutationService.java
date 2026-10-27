package com.htv.farmverse.identity.authorization.application;

import com.htv.farmverse.common.exception.ConflictException;
import com.htv.farmverse.common.exception.NotFoundException;
import com.htv.farmverse.identity.authorization.domain.PermissionCode;
import com.htv.farmverse.identity.authorization.domain.PermissionEntity;
import com.htv.farmverse.identity.authorization.repository.PermissionRepository;
import com.htv.farmverse.identity.authorization.repository.RolePermissionRepository;

import java.util.Locale;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single mutation boundary for the global permission catalog.
 *
 * <p>Runtime mutations always identify the tenant whose effective authorization
 * policy changes. Each successful catalog mutation is coordinated with the
 * corresponding tenant-wide authorization invalidation.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionCatalogMutationService {

    private static final String WILDCARD_PERMISSION = "*";

    private final PermissionRepository permissions;
    private final RolePermissionRepository grants;

    @Transactional
    public PermissionEntity createGlobal(
            String code,
            String resourceType,
            String action,
            String description
    ) {
        return createInternal(code, resourceType, action, description);
    }

    @Transactional
    public PermissionEntity updateGlobal(
            String code,
            String resourceType,
            String action,
            String description
    ) {
        return updateInternal(code, resourceType, action, description);
    }

    @Transactional
    public boolean deleteGlobal(String code) {
        return deleteInternal(code);
    }

    PermissionEntity createInternal(
            String code,
            String resourceType,
            String action,
            String description
    ) {
        String normalizedCode = PermissionCode.normalize(code);
        String normalizedResourceType = normalizePart(
                resourceType,
                "resourceType"
        );
        String normalizedAction = normalizePart(
                action,
                "action"
        );

        guardProtected(normalizedCode);

        requireCodeMatchesMetadata(
                normalizedCode,
                normalizedResourceType,
                normalizedAction
        );

        log.debug(
                "Creating normalized permission catalog entry: "
                        + "permissionCode={}, resourceType={}, action={}",
                normalizedCode,
                normalizedResourceType,
                normalizedAction
        );

        if (permissions.existsById(normalizedCode)) {
            log.warn(
                    "Permission creation rejected because the code already "
                            + "exists: permissionCode={}",
                    normalizedCode
            );

            throw new ConflictException(
                    "PERMISSION_CODE_ALREADY_EXISTS",
                    "Permission code already exists"
            );
        }

        if (permissions.existsByResourceTypeAndAction(
                normalizedResourceType,
                normalizedAction
        )) {
            log.warn(
                    "Permission creation rejected because resource type and "
                            + "action already exist: resourceType={}, action={}",
                    normalizedResourceType,
                    normalizedAction
            );

            throw new ConflictException(
                    "PERMISSION_ALREADY_EXISTS",
                    "Permission already exists for resource and action"
            );
        }

        PermissionEntity permission = new PermissionEntity(
                normalizedCode,
                normalizedResourceType,
                normalizedAction,
                clean(description)
        );

        PermissionEntity savedPermission =
                permissions.save(permission);

        log.debug(
                "Normalized permission catalog entry persisted: "
                        + "permissionCode={}, resourceType={}, action={}",
                savedPermission.getCode(),
                savedPermission.getResourceType(),
                savedPermission.getAction()
        );

        return savedPermission;
    }

    PermissionEntity updateInternal(
            String code,
            String resourceType,
            String action,
            String description
    ) {
        String normalizedCode = PermissionCode.normalize(code);
        String normalizedResourceType = normalizePart(
                resourceType,
                "resourceType"
        );
        String normalizedAction = normalizePart(
                action,
                "action"
        );

        guardProtected(normalizedCode);

        requireCodeMatchesMetadata(
                normalizedCode,
                normalizedResourceType,
                normalizedAction
        );

        log.debug(
                "Updating normalized permission catalog entry: "
                        + "permissionCode={}, resourceType={}, action={}",
                normalizedCode,
                normalizedResourceType,
                normalizedAction
        );

        PermissionEntity permission = permissions
                .findById(normalizedCode)
                .orElseThrow(() -> {
                    log.warn(
                            "Permission update rejected because the permission "
                                    + "was not found: permissionCode={}",
                            normalizedCode
                    );

                    return NotFoundException.entity(
                            "Permission",
                            normalizedCode
                    );
                });

        permissions.findByResourceTypeAndAction(
                        normalizedResourceType,
                        normalizedAction
                )
                .filter(existingPermission ->
                        !existingPermission.getCode().equals(normalizedCode)
                )
                .ifPresent(existingPermission -> {
                    log.warn(
                            "Permission update rejected because resource type "
                                    + "and action belong to another permission: "
                                    + "permissionCode={}, conflictingCode={}, "
                                    + "resourceType={}, action={}",
                            normalizedCode,
                            existingPermission.getCode(),
                            normalizedResourceType,
                            normalizedAction
                    );

                    throw new ConflictException(
                            "PERMISSION_ALREADY_EXISTS",
                            "Permission already exists for resource and action"
                    );
                });

        permission.updateMetadata(
                normalizedResourceType,
                normalizedAction,
                clean(description)
        );

        log.debug(
                "Normalized permission catalog entry updated: "
                        + "permissionCode={}, resourceType={}, action={}",
                normalizedCode,
                normalizedResourceType,
                normalizedAction
        );

        return permission;
    }

    boolean deleteInternal(String code) {
        String normalizedCode = PermissionCode.normalize(code);

        guardProtected(normalizedCode);

        log.debug(
                "Deleting normalized permission catalog entry: "
                        + "permissionCode={}",
                normalizedCode
        );

        if (!permissions.existsById(normalizedCode)) {
            log.debug(
                    "Permission catalog entry does not exist: "
                            + "permissionCode={}",
                    normalizedCode
            );

            return false;
        }

        long grantCount =
                grants.countByIdPermissionCode(normalizedCode);

        if (grantCount > 0) {
            log.warn(
                    "Permission deletion rejected because the permission is "
                            + "assigned to roles: permissionCode={}, grantCount={}",
                    normalizedCode,
                    grantCount
            );

            throw new ConflictException(
                    "PERMISSION_IN_USE",
                    "Revoke permission from all roles before deleting it"
            );
        }

        permissions.deleteById(normalizedCode);

        log.debug(
                "Normalized permission catalog entry deleted: "
                        + "permissionCode={}",
                normalizedCode
        );

        return true;
    }

    private static void requireCodeMatchesMetadata(
            String code,
            String resourceType,
            String action
    ) {
        String codeResourceType =
                PermissionCode.resourceType(code);

        String codeAction =
                PermissionCode.action(code);

        if (!codeResourceType.equals(resourceType)
                || !codeAction.equals(action)) {
            log.warn(
                    "Permission metadata validation failed: "
                            + "permissionCode={}, expectedResourceType={}, "
                            + "actualResourceType={}, expectedAction={}, "
                            + "actualAction={}",
                    code,
                    codeResourceType,
                    resourceType,
                    codeAction,
                    action
            );

            throw new IllegalArgumentException(
                    "permission code must match resourceType and action"
            );
        }
    }

    private static void guardProtected(String code) {
        if (WILDCARD_PERMISSION.equals(code)) {
            log.warn(
                    "Protected permission mutation rejected: "
                            + "permissionCode={}",
                    code
            );

            throw new ConflictException(
                    "PROTECTED_PERMISSION",
                    "Wildcard permission is protected"
            );
        }
    }

    private static String normalizePart(
            String value,
            String field
    ) {
        if (value == null || value.isBlank()) {
            log.warn(
                    "Permission catalog validation failed: "
                            + "required field is blank, field={}",
                    field
            );

            throw new IllegalArgumentException(
                    field + " must not be blank"
            );
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String clean(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }
}