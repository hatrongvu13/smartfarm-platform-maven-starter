package com.htv.smartfarm.identity.authorization.application;

import com.htv.smartfarm.common.exception.ConflictException;
import com.htv.smartfarm.common.exception.NotFoundException;
import com.htv.smartfarm.identity.authorization.domain.MembershipRoleEntity;
import com.htv.smartfarm.identity.authorization.domain.PermissionEntity;
import com.htv.smartfarm.identity.authorization.domain.RoleEntity;
import com.htv.smartfarm.identity.authorization.domain.RolePermissionEntity;
import com.htv.smartfarm.identity.authorization.domain.RolePermissionId;
import com.htv.smartfarm.identity.authorization.repository.MembershipRoleRepository;
import com.htv.smartfarm.identity.authorization.repository.PermissionRepository;
import com.htv.smartfarm.identity.authorization.repository.RolePermissionRepository;
import com.htv.smartfarm.identity.authorization.repository.RoleRepository;
import com.htv.smartfarm.identity.authorization.version.AuthorizationMutationCoordinator;
import com.htv.smartfarm.identity.messaging.event.IdentityIntegrationEventPublisher;
import com.htv.smartfarm.identity.tenant.domain.MembershipStatus;
import com.htv.smartfarm.identity.tenant.domain.TenantEntity;
import com.htv.smartfarm.identity.tenant.domain.TenantMembershipEntity;
import com.htv.smartfarm.identity.tenant.repository.TenantMembershipRepository;
import com.htv.smartfarm.identity.tenant.repository.TenantRepository;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleManagementService {

    private static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";
    private static final String SUPERADMIN = "SUPERADMIN";

    private static final String ROLE_CREATED_EVENT =
            "identity.role.created";

    private static final String ROLE_PERMISSION_GRANTED_EVENT =
            "identity.role.permission-granted";

    private static final String ROLE_PERMISSION_REVOKED_EVENT =
            "identity.role.permission-revoked";

    private static final String ROLE_ASSIGNED_EVENT =
            "identity.role.assigned";

    private static final String ROLE_REVOKED_EVENT =
            "identity.role.revoked";

    private static final String ROLE_AGGREGATE_TYPE = "role";
    private static final String MEMBERSHIP_AGGREGATE_TYPE = "membership";

    private static final String ROLE_PERMISSION_GRANTED_REASON =
            "ROLE_PERMISSION_GRANTED";

    private static final String ROLE_PERMISSION_REVOKED_REASON =
            "ROLE_PERMISSION_REVOKED";

    private static final String ROLE_ASSIGNED_REASON =
            "ROLE_ASSIGNED";

    private static final String ROLE_REVOKED_REASON =
            "ROLE_REVOKED";

    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final MembershipRoleRepository membershipRoleRepository;
    private final IdentityIntegrationEventPublisher events;
    private final AuthorizationMutationCoordinator authorizationMutations;

    @Transactional
    public String createRole(
            String tenantId,
            String code,
            String name,
            boolean systemRole
    ) {
        return createRole(
                tenantId,
                code,
                name,
                systemRole,
                null,
                null
        );
    }

    @Transactional
    public String createRole(
            String tenantId,
            String code,
            String name,
            boolean systemRole,
            String actorId,
            String correlationId
    ) {
        requireText(tenantId, "tenantId");
        requireText(code, "code");
        requireText(name, "name");

        String normalizedCode = normalizeCode(code);

        log.debug(
                "Creating tenant role: tenantId={}, roleCode={}, "
                        + "systemRole={}, actorId={}, correlationId={}",
                tenantId,
                normalizedCode,
                systemRole,
                actorId,
                correlationId
        );

        if (roleRepository.existsByTenantIdAndCode(
                tenantId,
                normalizedCode
        )) {
            log.warn(
                    "Role creation rejected because the role code already exists: "
                            + "tenantId={}, roleCode={}, actorId={}, correlationId={}",
                    tenantId,
                    normalizedCode,
                    actorId,
                    correlationId
            );

            throw new ConflictException(
                    "ROLE_CODE_ALREADY_EXISTS",
                    "Role code already exists in tenant"
            );
        }

        TenantEntity tenant = tenantRepository
                .findById(tenantId)
                .orElseThrow(() -> {
                    log.warn(
                            "Role creation rejected because the tenant was not found: "
                                    + "tenantId={}, roleCode={}",
                            tenantId,
                            normalizedCode
                    );

                    return NotFoundException.entity(
                            "Tenant",
                            tenantId
                    );
                });

        RoleEntity role = new RoleEntity(
                UUID.randomUUID().toString(),
                tenant,
                normalizedCode,
                name.trim(),
                systemRole
        );

        roleRepository.save(role);

        String eventId = events.publish(
                tenantId,
                actorId,
                correlationId,
                null,
                ROLE_CREATED_EVENT,
                ROLE_AGGREGATE_TYPE,
                role.getId(),
                role.getVersion(),
                Map.of(
                        "roleId", role.getId(),
                        "roleCode", role.getCode(),
                        "name", role.getName(),
                        "systemRole", role.isSystemRole()
                )
        );

        log.info(
                "Tenant role created successfully: tenantId={}, roleId={}, "
                        + "roleCode={}, systemRole={}, eventId={}, actorId={}, "
                        + "correlationId={}",
                tenantId,
                role.getId(),
                role.getCode(),
                role.isSystemRole(),
                eventId,
                actorId,
                correlationId
        );

        return role.getId();
    }

    @Transactional
    public void createPermission(
            String code,
            String resourceType,
            String action,
            String description
    ) {
        requireText(code, "code");
        requireText(resourceType, "resourceType");
        requireText(action, "action");

        String normalizedCode = normalizePermissionCode(code);
        String normalizedResourceType =
                normalizePermissionPart(resourceType);
        String normalizedAction =
                normalizePermissionPart(action);

        log.debug(
                "Creating permission: permissionCode={}, "
                        + "resourceType={}, action={}",
                normalizedCode,
                normalizedResourceType,
                normalizedAction
        );

        if (permissionRepository.existsById(normalizedCode)) {
            log.warn(
                    "Permission creation rejected because the code already exists: "
                            + "permissionCode={}",
                    normalizedCode
            );

            throw new ConflictException(
                    "PERMISSION_CODE_ALREADY_EXISTS",
                    "Permission code already exists"
            );
        }

        if (permissionRepository.existsByResourceTypeAndAction(
                normalizedResourceType,
                normalizedAction
        )) {
            log.warn(
                    "Permission creation rejected because resource and action "
                            + "already exist: resourceType={}, action={}",
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
                normalizeNullable(description)
        );

        permissionRepository.save(permission);

        log.info(
                "Permission created successfully: permissionCode={}, "
                        + "resourceType={}, action={}",
                permission.getCode(),
                permission.getResourceType(),
                permission.getAction()
        );
    }

    @Transactional
    public void grantPermissionToRole(
            String tenantId,
            String roleCode,
            String permissionCode
    ) {
        grantPermissionToRole(
                tenantId,
                roleCode,
                permissionCode,
                null,
                null
        );
    }

    @Transactional
    public void grantPermissionToRole(
            String tenantId,
            String roleCode,
            String permissionCode,
            String actorId,
            String correlationId
    ) {
        requireText(permissionCode, "permissionCode");

        RoleEntity role = getRole(tenantId, roleCode);

        String normalizedPermissionCode =
                normalizePermissionCode(permissionCode);

        log.debug(
                "Granting permission to role: tenantId={}, roleId={}, "
                        + "roleCode={}, permissionCode={}, actorId={}, "
                        + "correlationId={}",
                tenantId,
                role.getId(),
                role.getCode(),
                normalizedPermissionCode,
                actorId,
                correlationId
        );

        PermissionEntity permission = permissionRepository
                .findById(normalizedPermissionCode)
                .orElseThrow(() -> {
                    log.warn(
                            "Permission grant rejected because the permission "
                                    + "was not found: tenantId={}, roleCode={}, "
                                    + "permissionCode={}",
                            tenantId,
                            role.getCode(),
                            normalizedPermissionCode
                    );

                    return NotFoundException.entity(
                            "Permission",
                            normalizedPermissionCode
                    );
                });

        if (rolePermissionRepository
                .existsByIdRoleIdAndIdPermissionCode(
                        role.getId(),
                        permission.getCode()
                )) {
            log.debug(
                    "Permission grant skipped because it already exists: "
                            + "tenantId={}, roleId={}, roleCode={}, "
                            + "permissionCode={}",
                    tenantId,
                    role.getId(),
                    role.getCode(),
                    permission.getCode()
            );

            return;
        }

        rolePermissionRepository.save(
                new RolePermissionEntity(
                        role,
                        permission
                )
        );

        String eventId = events.publish(
                tenantId,
                actorId,
                correlationId,
                null,
                ROLE_PERMISSION_GRANTED_EVENT,
                ROLE_AGGREGATE_TYPE,
                role.getId(),
                role.getVersion(),
                Map.of(
                        "roleId", role.getId(),
                        "roleCode", role.getCode(),
                        "permissionCode", permission.getCode(),
                        "resourceType", permission.getResourceType(),
                        "action", permission.getAction()
                )
        );

        rolePermissionAuthorizationChanged(
                tenantId,
                actorId,
                correlationId,
                ROLE_PERMISSION_GRANTED_REASON
        );

        log.info(
                "Permission granted to role successfully: tenantId={}, "
                        + "roleId={}, roleCode={}, permissionCode={}, "
                        + "eventId={}, actorId={}, correlationId={}",
                tenantId,
                role.getId(),
                role.getCode(),
                permission.getCode(),
                eventId,
                actorId,
                correlationId
        );
    }

    @Transactional
    public void revokePermissionFromRole(
            String tenantId,
            String roleCode,
            String permissionCode
    ) {
        revokePermissionFromRole(
                tenantId,
                roleCode,
                permissionCode,
                null,
                null
        );
    }

    @Transactional
    public void revokePermissionFromRole(
            String tenantId,
            String roleCode,
            String permissionCode,
            String actorId,
            String correlationId
    ) {
        requireText(permissionCode, "permissionCode");

        RoleEntity role = getRole(tenantId, roleCode);

        String normalizedPermissionCode =
                normalizePermissionCode(permissionCode);

        RolePermissionId rolePermissionId = new RolePermissionId(
                role.getId(),
                normalizedPermissionCode
        );

        log.debug(
                "Revoking permission from role: tenantId={}, roleId={}, "
                        + "roleCode={}, permissionCode={}, actorId={}, "
                        + "correlationId={}",
                tenantId,
                role.getId(),
                role.getCode(),
                normalizedPermissionCode,
                actorId,
                correlationId
        );

        if (!rolePermissionRepository.existsById(rolePermissionId)) {
            log.debug(
                    "Permission revocation skipped because the assignment "
                            + "does not exist: tenantId={}, roleId={}, "
                            + "roleCode={}, permissionCode={}",
                    tenantId,
                    role.getId(),
                    role.getCode(),
                    normalizedPermissionCode
            );

            return;
        }

        rolePermissionRepository.deleteById(rolePermissionId);

        String eventId = events.publish(
                tenantId,
                actorId,
                correlationId,
                null,
                ROLE_PERMISSION_REVOKED_EVENT,
                ROLE_AGGREGATE_TYPE,
                role.getId(),
                role.getVersion(),
                Map.of(
                        "roleId", role.getId(),
                        "roleCode", role.getCode(),
                        "permissionCode", normalizedPermissionCode
                )
        );

        rolePermissionAuthorizationChanged(
                tenantId,
                actorId,
                correlationId,
                ROLE_PERMISSION_REVOKED_REASON
        );

        log.info(
                "Permission revoked from role successfully: tenantId={}, "
                        + "roleId={}, roleCode={}, permissionCode={}, "
                        + "eventId={}, actorId={}, correlationId={}",
                tenantId,
                role.getId(),
                role.getCode(),
                normalizedPermissionCode,
                eventId,
                actorId,
                correlationId
        );
    }

    @Transactional
    public void assignRole(
            String tenantId,
            String userId,
            String roleCode,
            String grantedBy
    ) {
        requireText(tenantId, "tenantId");
        requireText(userId, "userId");
        requireText(roleCode, "roleCode");
        requireText(grantedBy, "grantedBy");

        String normalizedRoleCode = normalizeCode(roleCode);

        log.debug(
                "Assigning role to tenant membership: tenantId={}, "
                        + "subjectId={}, roleCode={}, grantedBy={}",
                tenantId,
                userId,
                normalizedRoleCode,
                grantedBy
        );

        rejectPlatformRoleDelegation(normalizedRoleCode);

        TenantMembershipEntity membership = membershipRepository
                .findByTenantAndUserForUpdate(
                        tenantId,
                        userId
                )
                .orElseThrow(() -> {
                    log.warn(
                            "Role assignment rejected because the membership "
                                    + "was not found: tenantId={}, subjectId={}, "
                                    + "roleCode={}",
                            tenantId,
                            userId,
                            normalizedRoleCode
                    );

                    return NotFoundException.entity(
                            "TenantMembership",
                            tenantId + ":" + userId
                    );
                });

        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            log.warn(
                    "Role assignment rejected because the membership is not "
                            + "active: tenantId={}, subjectId={}, "
                            + "membershipId={}, membershipStatus={}, roleCode={}",
                    tenantId,
                    userId,
                    membership.getId(),
                    membership.getStatus(),
                    normalizedRoleCode
            );

            throw new ConflictException(
                    "MEMBERSHIP_NOT_ACTIVE",
                    "Role can only be assigned to an active membership"
            );
        }

        RoleEntity role = getRole(
                tenantId,
                normalizedRoleCode
        );

        if (membershipRoleRepository
                .existsByIdMembershipIdAndIdRoleId(
                        membership.getId(),
                        role.getId()
                )) {
            log.debug(
                    "Role assignment skipped because it already exists: "
                            + "tenantId={}, subjectId={}, membershipId={}, "
                            + "roleId={}, roleCode={}",
                    tenantId,
                    userId,
                    membership.getId(),
                    role.getId(),
                    role.getCode()
            );

            return;
        }

        String normalizedGrantedBy = grantedBy.trim();

        membershipRoleRepository.save(
                new MembershipRoleEntity(
                        membership,
                        role,
                        normalizedGrantedBy
                )
        );

        String eventId = events.publish(
                tenantId,
                normalizedGrantedBy,
                null,
                null,
                ROLE_ASSIGNED_EVENT,
                MEMBERSHIP_AGGREGATE_TYPE,
                membership.getId(),
                0,
                Map.of(
                        "membershipId", membership.getId(),
                        "subjectId", userId,
                        "roleId", role.getId(),
                        "roleCode", role.getCode()
                )
        );

        roleAuthorizationChanged(
                tenantId,
                userId,
                normalizedGrantedBy,
                null,
                ROLE_ASSIGNED_REASON
        );

        log.info(
                "Role assigned successfully: tenantId={}, subjectId={}, "
                        + "membershipId={}, roleId={}, roleCode={}, "
                        + "grantedBy={}, eventId={}",
                tenantId,
                userId,
                membership.getId(),
                role.getId(),
                role.getCode(),
                normalizedGrantedBy,
                eventId
        );
    }

    @Transactional
    public void revokeRole(
            String tenantId,
            String userId,
            String roleCode
    ) {
        requireText(tenantId, "tenantId");
        requireText(userId, "userId");
        requireText(roleCode, "roleCode");

        String normalizedRoleCode = normalizeCode(roleCode);

        log.debug(
                "Revoking role from tenant membership: tenantId={}, "
                        + "subjectId={}, roleCode={}",
                tenantId,
                userId,
                normalizedRoleCode
        );

        rejectPlatformRoleDelegation(normalizedRoleCode);

        TenantMembershipEntity membership = membershipRepository
                .findByTenantAndUserForUpdate(
                        tenantId,
                        userId
                )
                .orElseThrow(() -> {
                    log.warn(
                            "Role revocation rejected because the membership "
                                    + "was not found: tenantId={}, subjectId={}, "
                                    + "roleCode={}",
                            tenantId,
                            userId,
                            normalizedRoleCode
                    );

                    return NotFoundException.entity(
                            "TenantMembership",
                            tenantId + ":" + userId
                    );
                });

        RoleEntity role = getRole(
                tenantId,
                normalizedRoleCode
        );

        boolean assigned = membershipRoleRepository
                .existsByIdMembershipIdAndIdRoleId(
                        membership.getId(),
                        role.getId()
                );

        if (!assigned) {
            log.debug(
                    "Role revocation skipped because the assignment does not "
                            + "exist: tenantId={}, subjectId={}, membershipId={}, "
                            + "roleId={}, roleCode={}",
                    tenantId,
                    userId,
                    membership.getId(),
                    role.getId(),
                    role.getCode()
            );

            return;
        }

        membershipRoleRepository.deleteByMembershipIdAndRoleId(
                membership.getId(),
                role.getId()
        );

        String eventId = events.publish(
                tenantId,
                userId,
                null,
                null,
                ROLE_REVOKED_EVENT,
                MEMBERSHIP_AGGREGATE_TYPE,
                membership.getId(),
                0,
                Map.of(
                        "membershipId", membership.getId(),
                        "subjectId", userId,
                        "roleId", role.getId(),
                        "roleCode", role.getCode()
                )
        );

        roleAuthorizationChanged(
                tenantId,
                userId,
                userId,
                null,
                ROLE_REVOKED_REASON
        );

        log.info(
                "Role revoked successfully: tenantId={}, subjectId={}, "
                        + "membershipId={}, roleId={}, roleCode={}, eventId={}",
                tenantId,
                userId,
                membership.getId(),
                role.getId(),
                role.getCode(),
                eventId
        );
    }

    @Transactional(readOnly = true)
    public List<String> getRolePermissions(
            String tenantId,
            String roleCode
    ) {
        RoleEntity role = getRole(
                tenantId,
                roleCode
        );

        List<String> permissionCodes =
                rolePermissionRepository.findPermissionCodesByRoleIds(
                        List.of(role.getId())
                );

        log.debug(
                "Role permissions resolved: tenantId={}, roleId={}, "
                        + "roleCode={}, permissionCount={}",
                tenantId,
                role.getId(),
                role.getCode(),
                permissionCodes.size()
        );

        return permissionCodes;
    }

    private RoleEntity getRole(
            String tenantId,
            String roleCode
    ) {
        requireText(tenantId, "tenantId");
        requireText(roleCode, "roleCode");

        String normalizedRoleCode = normalizeCode(roleCode);

        return roleRepository
                .findByTenantIdAndCode(
                        tenantId,
                        normalizedRoleCode
                )
                .orElseThrow(() -> {
                    log.warn(
                            "Role was not found: tenantId={}, roleCode={}",
                            tenantId,
                            normalizedRoleCode
                    );

                    return NotFoundException.entity(
                            "Role",
                            tenantId + ":" + normalizedRoleCode
                    );
                });
    }

    private void rejectPlatformRoleDelegation(
            String normalizedRoleCode
    ) {
        if (PLATFORM_ADMIN.equals(normalizedRoleCode)
                || SUPERADMIN.equals(normalizedRoleCode)) {
            log.warn(
                    "Platform role delegation rejected: roleCode={}",
                    normalizedRoleCode
            );

            throw new ConflictException(
                    "PLATFORM_ROLE_DELEGATION_NOT_ALLOWED",
                    "Platform roles cannot be delegated through tenant administration"
            );
        }
    }

    /**
     * Role codes are canonical uppercase identifiers.
     */
    private String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Permission codes are OAuth scope identifiers and are canonical lowercase.
     */
    private String normalizePermissionCode(String value) {
        requireText(value, "permissionCode");

        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePermissionPart(String value) {
        requireText(value, "permissionMetadata");

        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }

    private void requireText(
            String value,
            String field
    ) {
        if (value == null || value.isBlank()) {
            log.warn(
                    "Role management validation failed: "
                            + "required field is blank, field={}",
                    field
            );

            throw new IllegalArgumentException(
                    field + " must not be blank"
            );
        }
    }

    // DYNAMIC_AUTH_PHASE2B_ROLE:
    // Spring injects this dependency. It may be null only in legacy tests
    // that construct RoleManagementService directly.
    private void roleAuthorizationChanged(
            String tenantId,
            String userId,
            String actorId,
            String correlationId,
            String reasonCode
    ) {
        if (authorizationMutations == null) {
            log.debug(
                    "Subject authorization invalidation skipped because the "
                            + "coordinator is unavailable: tenantId={}, "
                            + "subjectId={}, reasonCode={}",
                    tenantId,
                    userId,
                    reasonCode
            );

            return;
        }

        authorizationMutations.subjectChanged(
                tenantId,
                userId,
                actorId,
                correlationId,
                reasonCode
        );

        log.debug(
                "Subject authorization invalidation coordinated: "
                        + "tenantId={}, subjectId={}, actorId={}, "
                        + "reasonCode={}, correlationId={}",
                tenantId,
                userId,
                actorId,
                reasonCode,
                correlationId
        );
    }

    // DYNAMIC_AUTH_PHASE2C_ROLE_PERMISSION:
    // Role permission changes are tenant-wide because one role can affect
    // multiple subjects.
    private void rolePermissionAuthorizationChanged(
            String tenantId,
            String actorId,
            String correlationId,
            String reasonCode
    ) {
        if (authorizationMutations == null) {
            log.debug(
                    "Tenant authorization invalidation skipped because the "
                            + "coordinator is unavailable: tenantId={}, "
                            + "reasonCode={}",
                    tenantId,
                    reasonCode
            );

            return;
        }

        authorizationMutations.tenantChanged(
                tenantId,
                actorId,
                correlationId,
                reasonCode
        );

        log.debug(
                "Tenant authorization invalidation coordinated: "
                        + "tenantId={}, actorId={}, reasonCode={}, "
                        + "correlationId={}",
                tenantId,
                actorId,
                reasonCode,
                correlationId
        );
    }
}