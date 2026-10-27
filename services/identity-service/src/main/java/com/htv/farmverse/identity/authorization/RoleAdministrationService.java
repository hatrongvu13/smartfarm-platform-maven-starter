package com.htv.farmverse.identity.authorization;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.htv.farmverse.identity.shared.IdentityErrors;
import com.htv.farmverse.identity.tenant.Tenant;
import com.htv.farmverse.identity.tenant.TenantRepository;
import com.htv.farmverse.service.web.ApiProblem;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Quản lý role của một tenant và danh mục permission của nền tảng. */
@Service
@RequiredArgsConstructor
public class RoleAdministrationService {

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final TenantRepository tenants;

    // ---- Role (theo tenant) ----

    @Transactional(readOnly = true)
    public List<Role> listRoles(String tenantId) {
        return roles.findByTenantIdOrderByCode(tenantId);
    }

    @Transactional(readOnly = true)
    public Role getRole(String tenantId, String code) {
        return roles.findByTenantIdAndCode(tenantId, code)
                .orElseThrow(() -> ApiProblem.notFound(IdentityErrors.ROLE_NOT_FOUND, "Không có role " + code));
    }

    @Transactional
    public Role createRole(String tenantId, String code, String name, String description, Collection<String> permissionCodes) {
        if (roles.findByTenantIdAndCode(tenantId, code).isPresent()) {
            throw ApiProblem.conflict(IdentityErrors.ROLE_EXISTS, "Role " + code + " đã có");
        }
        Tenant tenant = tenants.getReferenceById(tenantId);
        Role role = new Role(tenant, code, name, description, false);
        role.replacePermissions(resolvePermissions(permissionCodes));
        return roles.save(role);
    }

    @Transactional
    public Role updateRole(String tenantId, String code, String name, String description, Collection<String> permissionCodes) {
        Role role = editable(tenantId, code);
        role.rename(name, description);
        if (permissionCodes != null) {
            role.replacePermissions(resolvePermissions(permissionCodes));
        }
        return role;
    }

    @Transactional
    public void deleteRole(String tenantId, String code) {
        Role role = editable(tenantId, code);
        roles.delete(role); // membership_role xóa theo ON DELETE CASCADE
    }

    /** Lấy role theo mã trong tenant; mã nào không có thì báo lỗi. */
    @Transactional(readOnly = true)
    public Set<Role> resolveRoles(String tenantId, Collection<String> codes) {
        Set<String> wanted = new HashSet<>(codes);
        List<Role> found = roles.findByTenantIdAndCodeIn(tenantId, wanted);
        if (found.size() != wanted.size()) {
            throw ApiProblem.badRequest(IdentityErrors.ROLE_NOT_FOUND, "Có mã role không tồn tại: " + wanted);
        }
        return new HashSet<>(found);
    }

    private Role editable(String tenantId, String code) {
        Role role = getRole(tenantId, code);
        if (role.isSystemOwned()) {
            throw ApiProblem.conflict(IdentityErrors.SYSTEM_ROLE_LOCKED, "Role hệ thống không sửa được");
        }
        return role;
    }

    // ---- Permission (toàn nền tảng) ----

    @Transactional(readOnly = true)
    public List<Permission> listPermissions() {
        return permissions.findAllByOrderByCode();
    }

    @Transactional
    public Permission createPermission(String code, String resourceType, String action, String description) {
        if (permissions.existsById(code)) {
            throw ApiProblem.conflict(IdentityErrors.PERMISSION_EXISTS, "Permission " + code + " đã có");
        }
        return permissions.save(new Permission(code, resourceType, action, description, false));
    }

    @Transactional
    public Permission updatePermission(String code, String description) {
        Permission permission = editablePermission(code);
        permission.describe(description);
        return permission;
    }

    @Transactional
    public void deletePermission(String code) {
        Permission permission = editablePermission(code);
        if (roles.existsByPermissionsCode(code)) {
            throw ApiProblem.conflict(IdentityErrors.PERMISSION_IN_USE, "Permission đang được role dùng");
        }
        permissions.delete(permission);
    }

    private Permission editablePermission(String code) {
        Permission permission = permissions.findById(code)
                .orElseThrow(() -> ApiProblem.notFound(IdentityErrors.PERMISSION_NOT_FOUND, "Không có permission " + code));
        if (permission.isSystemOwned()) {
            throw ApiProblem.conflict(IdentityErrors.SYSTEM_PERMISSION_LOCKED, "Permission hệ thống không sửa được");
        }
        return permission;
    }

    private Set<Permission> resolvePermissions(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Set.of();
        }
        Set<String> wanted = new HashSet<>(codes);
        List<Permission> found = permissions.findAllById(wanted);
        if (found.size() != wanted.size()) {
            throw ApiProblem.badRequest(IdentityErrors.PERMISSION_NOT_FOUND, "Có mã permission không tồn tại");
        }
        return new HashSet<>(found);
    }
}
