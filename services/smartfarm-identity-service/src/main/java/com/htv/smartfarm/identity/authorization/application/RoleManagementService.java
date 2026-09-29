package com.htv.smartfarm.identity.authorization.application;

import java.util.List;
import java.util.UUID;

import com.htv.smartfarm.identity.authorization.domain.MembershipRoleEntity;
import com.htv.smartfarm.identity.authorization.domain.PermissionEntity;
import com.htv.smartfarm.identity.authorization.domain.RoleEntity;
import com.htv.smartfarm.identity.authorization.domain.RolePermissionEntity;
import com.htv.smartfarm.identity.authorization.repository.MembershipRoleRepository;
import com.htv.smartfarm.identity.authorization.repository.PermissionRepository;
import com.htv.smartfarm.identity.authorization.repository.RolePermissionRepository;
import com.htv.smartfarm.identity.authorization.repository.RoleRepository;
import com.htv.smartfarm.identity.shared.exception.ConflictException;
import com.htv.smartfarm.identity.shared.exception.EntityNotFoundException;
import com.htv.smartfarm.identity.tenant.domain.MembershipStatus;
import com.htv.smartfarm.identity.tenant.domain.TenantEntity;
import com.htv.smartfarm.identity.tenant.domain.TenantMembershipEntity;
import com.htv.smartfarm.identity.tenant.repository.TenantMembershipRepository;
import com.htv.smartfarm.identity.tenant.repository.TenantRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleManagementService {

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
        requireText(code, "code");
        requireText(name, "name");

        if (roleRepository.existsByTenantIdAndCode(
                tenantId,
                code.trim()
        )) {
            throw new ConflictException(
                    "Role code already exists in tenant"
            );
        }

        TenantEntity tenant = tenantRepository
                .findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Tenant",
                        tenantId
                ));

        RoleEntity role = new RoleEntity(
                UUID.randomUUID().toString(),
                tenant,
                code.trim().toUpperCase(),
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

        if (permissionRepository.existsById(code)) {
            throw new ConflictException(
                    "Permission code already exists"
            );
        }

        if (permissionRepository.existsByResourceTypeAndAction(
                resourceType,
                action
        )) {
            throw new ConflictException(
                    "Permission already exists for resource and action"
            );
        }

        PermissionEntity permission = new PermissionEntity(
                code.trim().toUpperCase(),
                resourceType.trim().toUpperCase(),
                action.trim().toUpperCase(),
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
        RoleEntity role = getRole(tenantId, roleCode);

        PermissionEntity permission = permissionRepository
                .findById(permissionCode)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Permission",
                        permissionCode
                ));

        if (rolePermissionRepository
                .existsByRoleIdAndPermissionCode(
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
        RoleEntity role = getRole(tenantId, roleCode);

        rolePermissionRepository.deleteById(
                new com.htv.smartfarm.identity.authorization.domain
                        .RolePermissionId(
                        role.getId(),
                        permissionCode
                )
        );
    }

    @Transactional
    public void assignRole(
            String tenantId,
            String userId,
            String roleCode,
            String grantedBy
    ) {
        requireText(grantedBy, "grantedBy");

        TenantMembershipEntity membership = membershipRepository
                .findByTenantAndUserForUpdate(tenantId, userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "TenantMembership",
                        tenantId + ":" + userId
                ));

        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new ConflictException(
                    "Role can only be assigned to an active membership"
            );
        }

        RoleEntity role = getRole(tenantId, roleCode);

        if (membershipRoleRepository
                .existsByMembershipIdAndRoleId(
                        membership.getId(),
                        role.getId()
                )) {
            return;
        }

        membershipRoleRepository.save(
                new MembershipRoleEntity(
                        membership,
                        role,
                        grantedBy
                )
        );
    }

    @Transactional
    public void revokeRole(
            String tenantId,
            String userId,
            String roleCode
    ) {
        TenantMembershipEntity membership = membershipRepository
                .findByTenantAndUserForUpdate(tenantId, userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "TenantMembership",
                        tenantId + ":" + userId
                ));

        RoleEntity role = getRole(tenantId, roleCode);

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
                .findPermissionCodesByRoleIds(List.of(role.getId()));
    }

    private RoleEntity getRole(
            String tenantId,
            String roleCode
    ) {
        return roleRepository
                .findByTenantIdAndCode(
                        tenantId,
                        roleCode.trim().toUpperCase()
                )
                .orElseThrow(() -> new EntityNotFoundException(
                        "Role",
                        tenantId + ":" + roleCode
                ));
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