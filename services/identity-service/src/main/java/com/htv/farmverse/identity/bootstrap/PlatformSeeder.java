package com.htv.farmverse.identity.bootstrap;

import com.htv.farmverse.identity.account.AccountService;
import com.htv.farmverse.identity.account.UserAccount;
import com.htv.farmverse.identity.account.UserAccountRepository;
import com.htv.farmverse.identity.authorization.Permission;
import com.htv.farmverse.identity.authorization.PermissionRepository;
import com.htv.farmverse.identity.authorization.Role;
import com.htv.farmverse.identity.authorization.RoleRepository;
import com.htv.farmverse.identity.config.IdentityProperties;
import com.htv.farmverse.identity.tenant.Membership;
import com.htv.farmverse.identity.tenant.MembershipRepository;
import com.htv.farmverse.identity.tenant.Tenant;
import com.htv.farmverse.identity.tenant.TenantRepository;
import com.htv.farmverse.service.security.FarmVerseClaims;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dữ liệu tối thiểu để hệ thống dùng được, chạy mỗi lần khởi động và an toàn khi chạy lại:
 * <ol>
 *   <li>Danh mục permission từ {@code farmverse.identity.catalog} (đánh dấu system_owned).</li>
 *   <li>Tenant {@code platform} + role {@code SUPER_ADMIN}.</li>
 *   <li>Super admin đầu tiên nếu khai {@code farmverse.identity.bootstrap.admin-email} và email chưa có.</li>
 * </ol>
 * Đây là nơi duy nhất tự ghi dữ liệu lúc khởi động.
 */
@Slf4j
@Order(2)
@Component
@RequiredArgsConstructor
public class PlatformSeeder implements ApplicationRunner {

    private final IdentityProperties properties;
    private final PermissionRepository permissions;
    private final TenantRepository tenants;
    private final RoleRepository roles;
    private final MembershipRepository memberships;
    private final UserAccountRepository accounts;
    private final AccountService accountService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedCatalog();
        Tenant platform = tenants.findByCode(Tenant.PLATFORM_CODE)
                .orElseGet(() -> tenants.save(new Tenant(Tenant.PLATFORM_CODE, "FarmVerse Platform")));
        Role superAdmin = roles.findByTenantIdAndCode(platform.getId(), FarmVerseClaims.SUPER_ADMIN_ROLE)
                .orElseGet(() -> roles.save(new Role(platform, FarmVerseClaims.SUPER_ADMIN_ROLE,
                        "Super admin", "Toàn quyền nền tảng", true)));
        seedAdmin(platform, superAdmin);
    }

    private void seedCatalog() {
        for (IdentityProperties.PermissionSeed seed : properties.catalog().permissions()) {
            permissions.findById(seed.code()).ifPresentOrElse(
                    existing -> existing.describe(seed.description()),
                    () -> permissions.save(new Permission(seed.code(), seed.resourceType(), seed.action(),
                            seed.description(), true)));
        }
    }

    private void seedAdmin(Tenant platform, Role superAdmin) {
        IdentityProperties.Bootstrap bootstrap = properties.bootstrap();
        if (!bootstrap.enabled() || accounts.existsByNormalizedEmail(UserAccount.normalize(bootstrap.adminEmail()))) {
            return;
        }
        String name = bootstrap.adminDisplayName() == null ? "Super Admin" : bootstrap.adminDisplayName();
        UserAccount admin = accountService.create(bootstrap.adminEmail(), bootstrap.adminPassword(), name);
        Membership membership = new Membership(platform, admin);
        membership.addRole(superAdmin);
        memberships.save(membership);
        log.info("Created first super admin {}", bootstrap.adminEmail());
    }
}
