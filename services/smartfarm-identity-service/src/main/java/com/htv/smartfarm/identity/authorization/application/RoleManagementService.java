package com.htv.smartfarm.identity.authorization.application;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

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
import com.htv.smartfarm.identity.tenant.domain.MembershipStatus;
import com.htv.smartfarm.identity.tenant.domain.TenantEntity;
import com.htv.smartfarm.identity.tenant.domain.TenantMembershipEntity;
import com.htv.smartfarm.identity.tenant.repository.TenantMembershipRepository;
import com.htv.smartfarm.identity.tenant.repository.TenantRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleManagementService {

    private static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";
    private static final String SUPERADMIN = "SUPERADMIN";

    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final MembershipRoleRepository membershipRoleRepository;

    public RoleManagementService(
            TenantRepository tenantRepository,
            TenantMembershipRepository membershipRepository,
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            RolePermissionRepository rolePermissionRepository,
            MembershipRoleRepository membershipRoleRepository
    ) {
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.membershipRoleRepository = membershipRoleRepository;
    }

    @Transactional
    public String createRole(
            String tenantId,
            String code,
            String name,
            boolean systemRole
    ) {
        requireText(tenantId, "tenantId");
        requireText(code, "code");
        requireText(name, "name");

        String normalizedCode = normalizeCode(code);

        if (roleRepository.existsByTenantIdAndCode(
                tenantId,
                normalizedCode
        )) {
            throw new ConflictException(
                    "ROLE_CODE_ALREADY_EXISTS",
                    "Role code already exists in tenant"
            );
        }

        TenantEntity tenant = tenantRepository
                .findById(tenantId)
                .orElseThrow(() -> NotFoundException.entity(
                        "Tenant",
                        tenantId
                ));

        RoleEntity role = new RoleEntity(
                UUID.randomUUID().toString(),
                tenant,
                normalizedCode,
                name.trim(),
                systemRole
        );

        roleRepository.save(role);
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

        String normalizedCode = normalizeCode(code);
        String normalizedResourceType = normalizeCode(resourceType);
        String normalizedAction = normalizeCode(action);

        if (permissionRepository.existsById(normalizedCode)) {
            throw new ConflictException(
                    "PERMISSION_CODE_ALREADY_EXISTS",
                    "Permission code already exists"
            );
        }

        if (permissionRepository.existsByResourceTypeAndAction(
                normalizedResourceType,
                normalizedAction
        )) {
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
    }

    @Transactional
    public void grantPermissionToRole(
            String tenantId,
            String roleCode,
            String permissionCode
    ) {
        requireText(permissionCode, "permissionCode");

        RoleEntity role = getRole(tenantId, roleCode);
        String normalizedPermissionCode = normalizeCode(permissionCode);

        PermissionEntity permission = permissionRepository
                .findById(normalizedPermissionCode)
                .orElseThrow(() -> NotFoundException.entity(
                        "Permission",
                        normalizedPermissionCode
                ));

        if (rolePermissionRepository.existsByRoleIdAndPermissionCode(
                role.getId(),
                permission.getCode()
        )) {
            return;
        }

        rolePermissionRepository.save(
                new RolePermissionEntity(role, permission)
        );
    }

    @Transactional
    public void revokePermissionFromRole(
            String tenantId,
            String roleCode,
            String permissionCode
    ) {
        requireText(permissionCode, "permissionCode");

        RoleEntity role = getRole(tenantId, roleCode);
        String normalizedPermissionCode = normalizeCode(permissionCode);

        RolePermissionId id = new RolePermissionId(
                role.getId(),
                normalizedPermissionCode
        );

        if (!rolePermissionRepository.existsById(id)) {
            return;
        }

        rolePermissionRepository.deleteById(id);
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
        rejectPlatformRoleDelegation(normalizedRoleCode);

        TenantMembershipEntity membership = membershipRepository
                .findByTenantAndUserForUpdate(tenantId, userId)
                .orElseThrow(() -> NotFoundException.entity(
                        "TenantMembership",
                        tenantId + ":" + userId
                ));

        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new ConflictException(
                    "MEMBERSHIP_NOT_ACTIVE",
                    "Role can only be assigned to an active membership"
            );
        }

        RoleEntity role = getRole(tenantId, normalizedRoleCode);

        if (membershipRoleRepository.existsByMembershipIdAndRoleId(
                membership.getId(),
                role.getId()
        )) {
            return;
        }

        membershipRoleRepository.save(
                new MembershipRoleEntity(
                        membership,
                        role,
                        grantedBy.trim()
                )
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
        rejectPlatformRoleDelegation(normalizedRoleCode);

        TenantMembershipEntity membership = membershipRepository
                .findByTenantAndUserForUpdate(tenantId, userId)
                .orElseThrow(() -> NotFoundException.entity(
                        "TenantMembership",
                        tenantId + ":" + userId
                ));

        RoleEntity role = getRole(tenantId, normalizedRoleCode);

        membershipRoleRepository.deleteByMembershipIdAndRoleId(
                membership.getId(),
                role.getId()
        );
    }

    @Transactional(readOnly = true)
    public List<String> getRolePermissions(
            String tenantId,
            String roleCode
    ) {
        RoleEntity role = getRole(tenantId, roleCode);

        return rolePermissionRepository
                .findPermissionCodesByRoleIds(
                        List.of(role.getId())
                );
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
                .orElseThrow(() -> NotFoundException.entity(
                        "Role",
                        tenantId + ":" + normalizedRoleCode
                ));
    }

    private void rejectPlatformRoleDelegation(
            String normalizedRoleCode
    ) {
        if (PLATFORM_ADMIN.equals(normalizedRoleCode)
                || SUPERADMIN.equals(normalizedRoleCode)) {
            throw new ConflictException(
                    "PLATFORM_ROLE_DELEGATION_NOT_ALLOWED",
                    "Platform roles cannot be delegated through tenant administration"
            );
        }
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
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
            throw new IllegalArgumentException(
                    field + " must not be blank"
            );
        }
    }
}
