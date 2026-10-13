package com.htv.smartfarm.identity.authorization.application;

import com.htv.smartfarm.identity.authorization.domain.PermissionEntity;
import com.htv.smartfarm.identity.authorization.domain.RoleEntity;
import com.htv.smartfarm.identity.authorization.domain.RolePermissionEntity;
import com.htv.smartfarm.identity.authorization.repository.PermissionRepository;
import com.htv.smartfarm.identity.authorization.repository.RolePermissionRepository;
import com.htv.smartfarm.identity.authorization.repository.RoleRepository;
import com.htv.smartfarm.identity.config.IdentityAuthorizationProperties;
import com.htv.smartfarm.identity.tenant.repository.TenantRepository;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Synchronizes configured permissions and tenant roles with persisted
 * authorization data during application startup.
 *
 * <p>The synchronization is idempotent. Existing permissions may optionally
 * have their metadata updated, while missing roles and grants are created.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityAuthorizationSynchronizer implements ApplicationRunner {

    private static final String SUPERADMIN = "SUPERADMIN";
    private static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";

    private static final String TARGET_SEPARATOR = "\u0000";

    private final IdentityAuthorizationProperties properties;
    private final PermissionRepository permissions;
    private final TenantRepository tenants;
    private final RoleRepository roles;
    private final RolePermissionRepository grants;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Starting identity authorization synchronization");

        syncPermissions();
        syncRoles();

        log.info("Identity authorization synchronization completed successfully");
    }

    private void syncPermissions() {
        var permissionSync = properties.permissionSync();

        if (!permissionSync.enabled()) {
            return;
        }

        log.info(
                "Starting permission synchronization: "
                        + "configuredPermissionCount={}, updateExisting={}",
                permissionSync.permissions().size(),
                permissionSync.updateExisting()
        );

        Set<String> configuredCodes = new HashSet<>();
        Set<String> configuredTargets = new HashSet<>();

        int createdCount = 0;
        int updatedCount = 0;
        int unchangedCount = 0;

        for (var definition : permissionSync.permissions()) {
            String permissionCode = required(
                    definition.code(),
                    "permission.code"
            ).toLowerCase(Locale.ROOT);

            String resourceType = required(
                    definition.resourceType(),
                    "permission.resourceType"
            ).toLowerCase(Locale.ROOT);

            String action = required(
                    definition.action(),
                    "permission.action"
            ).toLowerCase(Locale.ROOT);

            validateUniquePermissionDefinition(
                    configuredCodes,
                    configuredTargets,
                    permissionCode,
                    resourceType,
                    action
            );

            PermissionEntity existingPermission =
                    permissions.findById(permissionCode)
                            .orElse(null);

            if (existingPermission == null) {
                permissions.save(
                        new PermissionEntity(
                                permissionCode,
                                resourceType,
                                action,
                                clean(definition.description())
                        )
                );

                createdCount++;

                log.info(
                        "Configured permission created: "
                                + "permissionCode={}, resourceType={}, action={}",
                        permissionCode,
                        resourceType,
                        action
                );

                continue;
            }

            if (permissionSync.updateExisting()) {
                existingPermission.updateMetadata(
                        resourceType,
                        action,
                        clean(definition.description())
                );

                updatedCount++;

                log.info(
                        "Configured permission updated: "
                                + "permissionCode={}, resourceType={}, action={}",
                        permissionCode,
                        resourceType,
                        action
                );

                continue;
            }

            unchangedCount++;
        }

        log.info(
                "Permission synchronization completed: configuredCount={}, "
                        + "createdCount={}, updatedCount={}, unchangedCount={}",
                permissionSync.permissions().size(),
                createdCount,
                updatedCount,
                unchangedCount
        );
    }

    private void syncRoles() {
        var roleSync = properties.roleSync();

        if (!roleSync.enabled()) {
            return;
        }

        var configuredRoles = roleSync.roles();
        var existingTenants = tenants.findAll();

        log.info(
                "Starting tenant role synchronization: tenantCount={}, "
                        + "configuredRoleCount={}",
                existingTenants.size(),
                configuredRoles.size()
        );

        int createdRoleCount = 0;
        int updatedRoleCount = 0;
        int createdGrantCount = 0;
        int skippedProtectedRoleCount = 0;

        for (var tenant : existingTenants) {
            for (var definition : configuredRoles) {
                String roleCode = required(
                        definition.code(),
                        "role.code"
                ).toUpperCase(Locale.ROOT);

                if (isProtectedPlatformRole(roleCode)) {
                    skippedProtectedRoleCount++;

                    log.debug(
                            "Configured role synchronization skipped for "
                                    + "protected platform role: tenantId={}, "
                                    + "roleCode={}",
                            tenant.getId(),
                            roleCode
                    );

                    continue;
                }

                String roleName = required(
                        definition.name(),
                        "role.name"
                );

                var existingRole = roles.findByTenantIdAndCode(
                        tenant.getId(),
                        roleCode
                );

                RoleEntity role;

                if (existingRole.isPresent()) {
                    role = existingRole.get();
                    role.rename(roleName);
                    updatedRoleCount++;
                } else {
                    role = roles.save(
                            new RoleEntity(
                                    UUID.randomUUID().toString(),
                                    tenant,
                                    roleCode,
                                    roleName,
                                    definition.systemRole()
                            )
                    );

                    createdRoleCount++;

                    log.info(
                            "Configured tenant role created: tenantId={}, "
                                    + "roleId={}, roleCode={}, systemRole={}",
                            tenant.getId(),
                            role.getId(),
                            role.getCode(),
                            role.isSystemRole()
                    );
                }

                createdGrantCount += syncRolePermissions(
                        tenant.getId(),
                        role,
                        definition.permissionCodes()
                );
            }
        }

        log.info(
                "Tenant role synchronization completed: tenantCount={}, "
                        + "configuredRoleCount={}, createdRoleCount={}, "
                        + "updatedRoleCount={}, createdGrantCount={}, "
                        + "skippedProtectedRoleCount={}",
                existingTenants.size(),
                configuredRoles.size(),
                createdRoleCount,
                updatedRoleCount,
                createdGrantCount,
                skippedProtectedRoleCount
        );
    }

    private int syncRolePermissions(
            String tenantId,
            RoleEntity role,
            Iterable<String> configuredPermissionCodes
    ) {
        int createdGrantCount = 0;

        for (String rawPermissionCode : configuredPermissionCodes) {
            String permissionCode = required(
                    rawPermissionCode,
                    "role.permissionCode"
            ).toLowerCase(Locale.ROOT);

            PermissionEntity permission = permissions
                    .findById(permissionCode)
                    .orElseThrow(() -> {
                        log.error(
                                "Role synchronization failed because a "
                                        + "configured permission was not found: "
                                        + "tenantId={}, roleCode={}, "
                                        + "permissionCode={}",
                                tenantId,
                                role.getCode(),
                                permissionCode
                        );

                        return new IllegalStateException(
                                "Configured permission not found: "
                                        + permissionCode
                        );
                    });

            if (grants.existsByIdRoleIdAndIdPermissionCode(
                    role.getId(),
                    permissionCode
            )) {
                continue;
            }

            grants.save(
                    new RolePermissionEntity(
                            role,
                            permission
                    )
            );

            createdGrantCount++;

            log.info(
                    "Configured permission granted to tenant role: "
                            + "tenantId={}, roleId={}, roleCode={}, "
                            + "permissionCode={}",
                    tenantId,
                    role.getId(),
                    role.getCode(),
                    permissionCode
            );
        }

        return createdGrantCount;
    }

    private void validateUniquePermissionDefinition(
            Set<String> configuredCodes,
            Set<String> configuredTargets,
            String permissionCode,
            String resourceType,
            String action
    ) {
        if (!configuredCodes.add(permissionCode)) {
            log.error(
                    "Permission synchronization configuration contains a "
                            + "duplicate permission code: permissionCode={}",
                    permissionCode
            );

            throw new IllegalStateException(
                    "Duplicate configured permission code: "
                            + permissionCode
            );
        }

        String permissionTarget =
                resourceType + TARGET_SEPARATOR + action;

        if (!configuredTargets.add(permissionTarget)) {
            log.error(
                    "Permission synchronization configuration contains a "
                            + "duplicate resource/action target: "
                            + "resourceType={}, action={}",
                    resourceType,
                    action
            );

            throw new IllegalStateException(
                    "Duplicate configured resource/action: "
                            + resourceType
                            + ":"
                            + action
            );
        }
    }

    private boolean isProtectedPlatformRole(String roleCode) {
        return SUPERADMIN.equals(roleCode)
                || PLATFORM_ADMIN.equals(roleCode);
    }

    private static String required(
            String value,
            String field
    ) {
        if (value == null || value.isBlank()) {
            log.error(
                    "Authorization synchronization configuration is invalid: "
                            + "required field is blank, field={}",
                    field
            );

            throw new IllegalStateException(
                    field + " must not be blank"
            );
        }

        return value.trim();
    }

    private static String clean(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }
}