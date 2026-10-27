package com.htv.farmverse.identity.tenant;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.htv.farmverse.identity.authorization.Permission;
import com.htv.farmverse.identity.authorization.PermissionRepository;
import com.htv.farmverse.identity.authorization.Role;
import com.htv.farmverse.identity.authorization.RoleRepository;
import com.htv.farmverse.identity.shared.IdentityErrors;
import com.htv.farmverse.service.web.ApiProblem;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tạo tenant mới (chỉ đội nền tảng). Mỗi tenant mới có sẵn role hệ thống ADMIN. */
@Service
@RequiredArgsConstructor
public class TenantAdministrationService {

    /** Role quản trị mặc định của mỗi tenant. */
    public static final String TENANT_ADMIN_ROLE = "ADMIN";

    /** Permission chỉ đội nền tảng được có, không cấp cho ADMIN của tenant thường. */
    static final Set<String> PLATFORM_ONLY = Set.of("identity:tenant:manage", "identity:permission:manage");

    private final TenantRepository tenants;
    private final RoleRepository roles;
    private final PermissionRepository permissions;

    @Transactional(readOnly = true)
    public List<Tenant> list() {
        return tenants.findAll();
    }

    @Transactional
    public Tenant create(String code, String name) {
        if (tenants.findByCode(code).isPresent()) {
            throw ApiProblem.conflict(IdentityErrors.TENANT_EXISTS, "Tenant " + code + " đã có");
        }
        Tenant tenant = tenants.save(new Tenant(code, name));
        Role admin = new Role(tenant, TENANT_ADMIN_ROLE, "Quản trị tenant", "Toàn quyền trong tenant", true);
        Set<Permission> adminPermissions = permissions.findAll().stream()
                .filter(permission -> !PLATFORM_ONLY.contains(permission.getCode()))
                .collect(Collectors.toSet());
        admin.replacePermissions(adminPermissions);
        roles.save(admin);
        return tenant;
    }
}
