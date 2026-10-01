package com.htv.smartfarm.identity.authorization.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import com.htv.smartfarm.common.exception.NotFoundException;
import com.htv.smartfarm.common.paging.PageResult;
import com.htv.smartfarm.identity.administration.application.model.RoleData;
import com.htv.smartfarm.identity.administration.application.model.RoleTypeData;
import com.htv.smartfarm.identity.authorization.application.model.PermissionData;
import com.htv.smartfarm.identity.authorization.domain.PermissionEntity;
import com.htv.smartfarm.identity.authorization.domain.RoleEntity;
import com.htv.smartfarm.identity.authorization.repository.PermissionRepository;
import com.htv.smartfarm.identity.authorization.repository.RolePermissionRepository;
import com.htv.smartfarm.identity.authorization.repository.RoleRepository;
import com.htv.smartfarm.identity.config.IdentitySettings;
import com.htv.smartfarm.identity.tenant.domain.TenantEntity;
import com.htv.smartfarm.identity.tenant.repository.TenantRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DefaultPlatformAuthorizationAdministrationService
        implements PlatformAuthorizationAdministrationService {

    private final IdentitySettings settings;
    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final RoleManagementService roleManagementService;

    public DefaultPlatformAuthorizationAdministrationService(
            IdentitySettings settings,
            TenantRepository tenantRepository,
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            RolePermissionRepository rolePermissionRepository,
            RoleManagementService roleManagementService
    ) {
        this.settings = settings;
        this.tenantRepository = tenantRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.roleManagementService = roleManagementService;
    }

    @Override
    public PageResult<RoleData> listPlatformRoles(int pageSize, String pageToken, boolean includePermissions) {
        TenantEntity tenant = platformTenant();
        List<RoleData> roles = roleRepository.findAllByTenantIdOrderByCodeAsc(tenant.getId()).stream()
                .filter(role -> role.isSystemRole() || isPlatformCode(role.getCode()))
                .map(role -> toData(role, includePermissions))
                .toList();
        return PageSupport.page(roles, pageSize, pageToken);
    }

    @Override
    @Transactional
    public RoleData createPlatformRole(String roleCode, String roleName, String description,
            List<String> permissionCodes, String actorId) {
        TenantEntity tenant = platformTenant();
        String code = normalize(roleCode);
        String id = roleManagementService.createRole(tenant.getId(), code, roleName, true);
        for (String permissionCode : new LinkedHashSet<>(permissionCodes == null ? List.of() : permissionCodes)) {
            roleManagementService.grantPermissionToRole(tenant.getId(), code, permissionCode);
        }
        RoleEntity role = roleRepository.findById(id)
                .orElseThrow(() -> NotFoundException.entity("Role", id));
        return toData(role, true);
    }

    @Override
    @Transactional
    public RoleData grantPermission(String roleCode, String permissionCode, String actorId) {
        TenantEntity tenant = platformTenant();
        roleManagementService.grantPermissionToRole(tenant.getId(), normalize(roleCode), permissionCode);
        return roleData(tenant.getId(), roleCode);
    }

    @Override
    @Transactional
    public RoleData revokePermission(String roleCode, String permissionCode, String actorId) {
        TenantEntity tenant = platformTenant();
        roleManagementService.revokePermissionFromRole(tenant.getId(), normalize(roleCode), permissionCode);
        return roleData(tenant.getId(), roleCode);
    }

    private RoleData roleData(String tenantId, String roleCode) {
        RoleEntity role = roleRepository.findByTenantIdAndCode(tenantId, normalize(roleCode))
                .orElseThrow(() -> NotFoundException.entity("Role", tenantId + ":" + roleCode));
        return toData(role, true);
    }

    private RoleData toData(RoleEntity role, boolean includePermissions) {
        List<PermissionData> permissions = includePermissions
                ? rolePermissionRepository.findAllByIdRoleId(role.getId()).stream()
                        .map(value -> value.getPermission())
                        .map(this::permissionData).toList()
                : List.of();
        return new RoleData(role.getId(), role.getTenant().getId(), role.getCode(), role.getName(),
                RoleTypeData.PLATFORM, permissions, role.getCreatedAt(), role.getUpdatedAt(), role.getVersion());
    }

    private PermissionData permissionData(PermissionEntity permission) {
        return new PermissionData(permission.getCode(), permission.getResourceType(),
                permission.getAction(), permission.getDescription());
    }

    private TenantEntity platformTenant() {
        String configured = settings.bootstrapTenant();
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("smartfarm.identity.bootstrap-tenant must be configured");
        }
        return tenantRepository.findById(configured)
                .or(() -> tenantRepository.findByCode(configured))
                .orElseThrow(() -> NotFoundException.entity("PlatformTenant", configured));
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("roleCode must not be blank");
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isPlatformCode(String code) {
        return "PLATFORM_ADMIN".equals(code) || "SUPERADMIN".equals(code);
    }
}
