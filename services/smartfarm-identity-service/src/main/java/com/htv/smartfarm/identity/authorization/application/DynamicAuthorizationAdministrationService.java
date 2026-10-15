package com.htv.smartfarm.identity.authorization.application;

import java.util.List;
import java.util.Locale;

import com.htv.smartfarm.common.exception.ConflictException;
import com.htv.smartfarm.common.exception.NotFoundException;
import com.htv.smartfarm.identity.authorization.domain.RoleEntity;
import com.htv.smartfarm.identity.authorization.domain.PermissionCode;
import com.htv.smartfarm.identity.authorization.repository.MembershipRoleRepository;
import com.htv.smartfarm.identity.authorization.repository.RolePermissionRepository;
import com.htv.smartfarm.identity.authorization.repository.RoleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicAuthorizationAdministrationService {

    private static final String USER_ROLE = "USER";
    private static final String ADMIN_ROLE = "ADMIN";
    private static final String SUPERADMIN_ROLE = "SUPERADMIN";
    private static final String PLATFORM_ADMIN_ROLE = "PLATFORM_ADMIN";

    private final RoleManagementService management;
    private final RoleRepository roles;
    private final RolePermissionRepository grants;
    private final MembershipRoleRepository assignments;
    @Transactional
    public String createRole(
            String tenantId,
            String code,
            String name,
            String actorId,
            String correlationId
    ) {
        String normalizedRoleCode =
                normalizeRole(code);

        guardPlatform(normalizedRoleCode);

        String roleId = management.createRole(
                tenantId,
                normalizedRoleCode,
                name,
                false,
                actorId,
                correlationId
        );

        log.info(
                "Role created: tenantId={}, roleId={}, roleCode={}, actorId={}, correlationId={}",
                tenantId,
                roleId,
                normalizedRoleCode,
                actorId,
                correlationId
        );

        return roleId;
    }

    @Transactional
    public void updateRole(
            String tenantId,
            String code,
            String name
    ) {
        RoleEntity role = role(
                tenantId,
                code
        );

        guardSystem(
                role.getCode(),
                role.isSystemRole()
        );

        role.rename(name);

        log.info(
                "Role updated: tenantId={}, roleId={}, roleCode={}, newName={}",
                tenantId,
                role.getId(),
                role.getCode(),
                name
        );
    }

    @Transactional
    public boolean deleteRole(
            String tenantId,
            String code
    ) {
        RoleEntity role = role(
                tenantId,
                code
        );

        guardSystem(
                role.getCode(),
                role.isSystemRole()
        );

        long assignmentCount =
                assignments.countAssignmentsByRoleIds(
                        List.of(role.getId())
                );

        if (assignmentCount > 0) {
            log.error(
                    "Role deletion rejected because role is assigned to memberships: "
                            + "tenantId={}, roleId={}, roleCode={}, assignmentCount={}",
                    tenantId,
                    role.getId(),
                    role.getCode(),
                    assignmentCount
            );

            throw new ConflictException(
                    "ROLE_IN_USE",
                    "Revoke role from all memberships before deleting it"
            );
        }

        grants.deleteAllByRoleId(role.getId());
        roles.delete(role);

        log.info(
                "Role deleted: tenantId={}, roleId={}, roleCode={}",
                tenantId,
                role.getId(),
                role.getCode()
        );

        return true;
    }

    @Transactional
    public void grant(
            String tenantId,
            String roleCode,
            String permissionCode,
            String actorId,
            String correlationId
    ) {
        String normalizedRoleCode =
                normalizeRole(roleCode);

        String normalizedPermissionCode =
                normalizePermission(permissionCode);

        guardPlatform(normalizedRoleCode);

        management.grantPermissionToRole(
                tenantId,
                normalizedRoleCode,
                normalizedPermissionCode,
                actorId,
                correlationId
        );

        log.info(
                "Permission granted to role: tenantId={}, roleCode={}, permissionCode={}, actorId={}, correlationId={}",
                tenantId,
                normalizedRoleCode,
                normalizedPermissionCode,
                actorId,
                correlationId
        );
    }

    @Transactional
    public void revoke(
            String tenantId,
            String roleCode,
            String permissionCode,
            String actorId,
            String correlationId
    ) {
        String normalizedRoleCode =
                normalizeRole(roleCode);

        String normalizedPermissionCode =
                normalizePermission(permissionCode);

        guardPlatform(normalizedRoleCode);

        management.revokePermissionFromRole(
                tenantId,
                normalizedRoleCode,
                normalizedPermissionCode,
                actorId,
                correlationId
        );

        log.info(
                "Permission revoked from role: tenantId={}, roleCode={}, permissionCode={}, actorId={}, correlationId={}",
                tenantId,
                normalizedRoleCode,
                normalizedPermissionCode,
                actorId,
                correlationId
        );
    }

    private RoleEntity role(
            String tenantId,
            String roleCode
    ) {
        String normalizedRoleCode =
                normalizeRole(roleCode);

        return roles.findByTenantIdAndCode(
                        tenantId,
                        normalizedRoleCode
                )
                .orElseThrow(() -> {
                    log.error(
                            "Role not found: tenantId={}, roleCode={}",
                            tenantId,
                            normalizedRoleCode
                    );

                    return NotFoundException.entity(
                            "Role",
                            tenantId + ":" + normalizedRoleCode
                    );
                });
    }

    private static void guardSystem(
            String roleCode,
            boolean systemRole
    ) {
        if (systemRole
                || USER_ROLE.equals(roleCode)
                || ADMIN_ROLE.equals(roleCode)
                || SUPERADMIN_ROLE.equals(roleCode)
                || PLATFORM_ADMIN_ROLE.equals(roleCode)) {

            throw new ConflictException(
                    "PROTECTED_ROLE",
                    "System role cannot be changed or deleted"
            );
        }
    }

    private static void guardPlatform(
            String roleCode
    ) {
        if (SUPERADMIN_ROLE.equals(roleCode)
                || PLATFORM_ADMIN_ROLE.equals(roleCode)) {

            throw new ConflictException(
                    "PLATFORM_ROLE_DELEGATION_NOT_ALLOWED",
                    "Platform roles cannot be managed through tenant administration"
            );
        }
    }

    private static String normalizePermission(
            String value
    ) {
        return PermissionCode.normalize(value);
    }

    private static String normalizeRole(
            String value
    ) {
        if (value == null
                || !value.trim()
                .matches("[A-Za-z][A-Za-z0-9_]{1,39}")) {

            throw new IllegalArgumentException(
                    "Invalid role code"
            );
        }

        return value.trim()
                .toUpperCase(Locale.ROOT);
    }
}