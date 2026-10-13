package com.htv.smartfarm.identity.bootstrap;

import com.htv.smartfarm.identity.authorization.domain.PermissionEntity;
import com.htv.smartfarm.identity.authorization.domain.RoleEntity;
import com.htv.smartfarm.identity.authorization.domain.RolePermissionEntity;
import com.htv.smartfarm.identity.authorization.domain.RolePermissionId;
import com.htv.smartfarm.identity.authorization.repository.PermissionRepository;
import com.htv.smartfarm.identity.authorization.repository.RolePermissionRepository;
import com.htv.smartfarm.identity.authorization.repository.RoleRepository;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent, retroactive fix that ensures every existing SUPERADMIN role has
 * the wildcard permission {@code *}.
 *
 * <p>The wildcard permission causes an access token to carry {@code scope: "*"},
 * allowing the gateway's {@code JwtAuthorities.isSuperAdmin} check for
 * {@code SCOPE_*} to recognize the principal as a super administrator.</p>
 *
 * <p>SUPERADMIN roles created by the previous automatic bootstrap process may
 * have no permissions. New administrators receive the wildcard grant through
 * {@link SuperAdminBootstrapService}. This runner repairs existing roles and
 * runs safely on every application startup, writing only missing grants.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuperAdminWildcardGrantRunner implements ApplicationRunner {

    private static final String WILDCARD_RESOURCE_TYPE = "*";
    private static final String WILDCARD_ACTION = "*";

    private static final String WILDCARD_DESCRIPTION =
            "Wildcard permission: grants every scope (root/super-admin)";

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        log.debug("Starting SUPERADMIN wildcard permission reconciliation");

        List<RoleEntity> superAdminRoles = findSuperAdminRoles();

        if (superAdminRoles.isEmpty()) {
            log.debug(
                    "SUPERADMIN wildcard permission reconciliation skipped: "
                            + "no SUPERADMIN roles found"
            );
            return;
        }

        PermissionEntity wildcardPermission =
                findOrCreateWildcardPermission();

        int grantedRoleCount = grantMissingWildcardPermissions(
                superAdminRoles,
                wildcardPermission
        );

        if (grantedRoleCount > 0) {
            log.info(
                    "Granted wildcard permission to existing SUPERADMIN roles: "
                            + "grantedRoleCount={}, totalSuperAdminRoleCount={}",
                    grantedRoleCount,
                    superAdminRoles.size()
            );
            return;
        }

        log.debug(
                "SUPERADMIN wildcard permission reconciliation completed: "
                        + "all roles already contain the wildcard permission, "
                        + "totalSuperAdminRoleCount={}",
                superAdminRoles.size()
        );
    }

    private List<RoleEntity> findSuperAdminRoles() {
        List<RoleEntity> superAdminRoles =
                roleRepository.findAll()
                        .stream()
                        .filter(this::isSuperAdminRole)
                        .toList();

        log.debug(
                "Loaded SUPERADMIN roles for wildcard reconciliation: count={}",
                superAdminRoles.size()
        );

        return superAdminRoles;
    }

    private boolean isSuperAdminRole(RoleEntity role) {
        return SuperAdminBootstrapService.SUPERADMIN.equals(
                role.getCode()
        );
    }

    private PermissionEntity findOrCreateWildcardPermission() {
        return permissionRepository
                .findById(
                        SuperAdminBootstrapService.WILDCARD_PERMISSION
                )
                .orElseGet(this::createWildcardPermission);
    }

    private PermissionEntity createWildcardPermission() {
        log.info(
                "Wildcard permission does not exist and will be created: "
                        + "permissionCode={}",
                SuperAdminBootstrapService.WILDCARD_PERMISSION
        );

        PermissionEntity wildcardPermission = new PermissionEntity(
                SuperAdminBootstrapService.WILDCARD_PERMISSION,
                WILDCARD_RESOURCE_TYPE,
                WILDCARD_ACTION,
                WILDCARD_DESCRIPTION
        );

        PermissionEntity savedPermission =
                permissionRepository.save(wildcardPermission);

        log.info(
                "Wildcard permission created successfully: permissionCode={}",
                savedPermission.getCode()
        );

        return savedPermission;
    }

    private int grantMissingWildcardPermissions(
            List<RoleEntity> superAdminRoles,
            PermissionEntity wildcardPermission
    ) {
        int grantedRoleCount = 0;

        for (RoleEntity role : superAdminRoles) {
            if (grantWildcardPermissionIfMissing(
                    role,
                    wildcardPermission
            )) {
                grantedRoleCount++;
            }
        }

        return grantedRoleCount;
    }

    private boolean grantWildcardPermissionIfMissing(
            RoleEntity role,
            PermissionEntity wildcardPermission
    ) {
        RolePermissionId rolePermissionId = new RolePermissionId(
                role.getId(),
                wildcardPermission.getCode()
        );

        if (rolePermissionRepository.existsById(rolePermissionId)) {
            log.debug(
                    "SUPERADMIN role already has wildcard permission: "
                            + "roleId={}, roleCode={}",
                    role.getId(),
                    role.getCode()
            );

            return false;
        }

        rolePermissionRepository.save(
                new RolePermissionEntity(
                        role,
                        wildcardPermission
                )
        );

        log.debug(
                "Wildcard permission granted to SUPERADMIN role: "
                        + "roleId={}, roleCode={}, permissionCode={}",
                role.getId(),
                role.getCode(),
                wildcardPermission.getCode()
        );

        return true;
    }
}