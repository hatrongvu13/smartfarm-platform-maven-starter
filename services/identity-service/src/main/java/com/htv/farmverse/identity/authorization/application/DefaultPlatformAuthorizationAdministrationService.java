package com.htv.farmverse.identity.authorization.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.htv.farmverse.common.exception.NotFoundException;
import com.htv.farmverse.common.paging.PageResult;
import com.htv.farmverse.identity.administration.application.model.RoleData;
import com.htv.farmverse.identity.administration.application.model.RoleTypeData;
import com.htv.farmverse.identity.authorization.application.model.PermissionData;
import com.htv.farmverse.identity.authorization.domain.PermissionEntity;
import com.htv.farmverse.identity.authorization.domain.RoleEntity;
import com.htv.farmverse.identity.authorization.domain.PermissionCode;
import com.htv.farmverse.identity.authorization.domain.RolePermissionEntity;
import com.htv.farmverse.identity.authorization.repository.RolePermissionRepository;
import com.htv.farmverse.identity.authorization.repository.RoleRepository;
import com.htv.farmverse.identity.config.IdentitySettings;
import com.htv.farmverse.identity.tenant.domain.TenantEntity;
import com.htv.farmverse.identity.tenant.repository.TenantRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultPlatformAuthorizationAdministrationService
        implements PlatformAuthorizationAdministrationService {

    private static final String PLATFORM_ADMIN_ROLE = "PLATFORM_ADMIN";
    private static final String SUPERADMIN_ROLE = "SUPERADMIN";

    private final IdentitySettings settings;
    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final RoleManagementService roleManagementService;

    @Override
    public PageResult<RoleData> listPlatformRoles(
            int pageSize,
            String pageToken,
            boolean includePermissions) {

        TenantEntity tenant = platformTenant();

        log.debug(
                "Listing platform roles: tenantId={}, pageSize={}, pageTokenPresent={}, includePermissions={}",
                tenant.getId(),
                pageSize,
                pageToken != null && !pageToken.isBlank(),
                includePermissions);

        List<RoleData> roles = roleRepository
                .findAllByTenantIdOrderByCodeAsc(tenant.getId())
                .stream()
                .filter(role -> role.isSystemRole() || isPlatformCode(role.getCode()))
                .map(role -> toData(role, includePermissions))
                .toList();

        log.debug(
                "Found {} platform roles for tenantId={}",
                roles.size(),
                tenant.getId());

        return PageSupport.page(roles, pageSize, pageToken);
    }

    @Override
    @Transactional
    public RoleData createPlatformRole(
            String roleCode,
            String roleName,
            String description,
            List<String> permissionCodes,
            String actorId,
            String correlationId) {

        TenantEntity tenant = platformTenant();
        String normalizedRoleCode = normalizeRoleCode(roleCode);
        Set<String> uniquePermissionCodes = uniquePermissionCodes(permissionCodes);

        log.info(
                "Creating platform role: tenantId={}, roleCode={}, permissionCount={}, actorId={}",
                tenant.getId(),
                normalizedRoleCode,
                uniquePermissionCodes.size(),
                actorId);

        String roleId = roleManagementService.createRole(
                tenant.getId(),
                normalizedRoleCode,
                roleName,
                true,
                actorId,
                correlationId,
                description);

        for (String permissionCode : uniquePermissionCodes) {
            log.debug(
                    "Granting permission while creating platform role: tenantId={}, roleCode={}, "
                            + "permissionCode={}",
                    tenant.getId(),
                    normalizedRoleCode,
                    permissionCode);

            roleManagementService.grantPermissionToRole(
                    tenant.getId(),
                    normalizedRoleCode,
                    permissionCode,
                    actorId,
                    correlationId);
        }

        RoleEntity role = findRoleById(roleId);

        log.info(
                "Platform role created successfully: tenantId={}, roleId={}, roleCode={}",
                tenant.getId(),
                roleId,
                normalizedRoleCode);

        return toData(role, true);
    }

    @Override
    @Transactional
    public RoleData grantPermission(
            String roleCode,
            String permissionCode,
            String actorId) {

        TenantEntity tenant = platformTenant();
        String normalizedRoleCode = normalizeRoleCode(roleCode);

        log.info(
                "Granting permission to platform role: tenantId={}, roleCode={}, "
                        + "permissionCode={}, actorId={}",
                tenant.getId(),
                normalizedRoleCode,
                permissionCode,
                actorId);

        roleManagementService.grantPermissionToRole(
                tenant.getId(),
                normalizedRoleCode,
                permissionCode,
                actorId,
                null);

        log.info(
                "Permission granted successfully: tenantId={}, roleCode={}, permissionCode={}",
                tenant.getId(),
                normalizedRoleCode,
                permissionCode);

        return roleData(tenant.getId(), normalizedRoleCode);
    }

    @Override
    @Transactional
    public RoleData revokePermission(
            String roleCode,
            String permissionCode,
            String actorId) {

        TenantEntity tenant = platformTenant();
        String normalizedRoleCode = normalizeRoleCode(roleCode);

        log.info(
                "Revoking permission from platform role: tenantId={}, roleCode={}, "
                        + "permissionCode={}, actorId={}",
                tenant.getId(),
                normalizedRoleCode,
                permissionCode,
                actorId);

        roleManagementService.revokePermissionFromRole(
                tenant.getId(),
                normalizedRoleCode,
                permissionCode,
                actorId,
                null);

        log.info(
                "Permission revoked successfully: tenantId={}, roleCode={}, permissionCode={}",
                tenant.getId(),
                normalizedRoleCode,
                permissionCode);

        return roleData(tenant.getId(), normalizedRoleCode);
    }

    private RoleData roleData(String tenantId, String normalizedRoleCode) {
        RoleEntity role = roleRepository
                .findByTenantIdAndCode(tenantId, normalizedRoleCode)
                .orElseThrow(() -> {
                    String identifier = tenantId + ":" + normalizedRoleCode;

                    log.warn(
                            "Platform role not found: tenantId={}, roleCode={}",
                            tenantId,
                            normalizedRoleCode);

                    return NotFoundException.entity("Role", identifier);
                });

        return toData(role, true);
    }

    private RoleEntity findRoleById(String roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> {
                    log.error(
                            "Role was created but could not be reloaded: roleId={}",
                            roleId);

                    return NotFoundException.entity("Role", roleId);
                });
    }

    private RoleData toData(RoleEntity role, boolean includePermissions) {
        List<PermissionData> permissions = includePermissions
                ? rolePermissionRepository.findAllByIdRoleId(role.getId())
                .stream()
                .map(RolePermissionEntity::getPermission)
                .map(this::toPermissionData)
                .toList()
                : List.of();

        return new RoleData(
                role.getId(),
                role.getTenant().getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                RoleTypeData.PLATFORM,
                permissions,
                role.getCreatedAt(),
                role.getUpdatedAt(),
                role.getVersion());
    }

    private PermissionData toPermissionData(PermissionEntity permission) {
        return new PermissionData(
                permission.getCode(),
                permission.getResourceType(),
                permission.getAction(),
                permission.getDescription());
    }

    private TenantEntity platformTenant() {
        String configuredTenant = settings.bootstrapTenant();

        if (configuredTenant == null || configuredTenant.isBlank()) {
            log.error("Bootstrap tenant is not configured");

            throw new IllegalStateException(
                    "farmverse.identity.bootstrap-tenant must be configured");
        }

        String tenantIdentifier = configuredTenant.trim();

        return tenantRepository.findById(tenantIdentifier)
                .or(() -> tenantRepository.findByCode(tenantIdentifier))
                .orElseThrow(() -> {
                    log.error(
                            "Configured platform tenant was not found: tenantIdentifier={}",
                            tenantIdentifier);

                    return NotFoundException.entity(
                            "PlatformTenant",
                            tenantIdentifier);
                });
    }

    private static Set<String> uniquePermissionCodes(List<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return Set.of();
        }

        return permissionCodes.stream()
                .map(PermissionCode::normalize)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static String normalizeRoleCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            throw new IllegalArgumentException("roleCode must not be blank");
        }

        return roleCode.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isPlatformCode(String roleCode) {
        return PLATFORM_ADMIN_ROLE.equals(roleCode)
                || SUPERADMIN_ROLE.equals(roleCode);
    }
}