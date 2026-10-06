package com.htv.smartfarm.identity.bootstrap;

import com.htv.smartfarm.identity.authorization.domain.PermissionEntity;
import com.htv.smartfarm.identity.authorization.domain.RoleEntity;
import com.htv.smartfarm.identity.authorization.domain.RolePermissionEntity;
import com.htv.smartfarm.identity.authorization.domain.RolePermissionId;
import com.htv.smartfarm.identity.authorization.repository.PermissionRepository;
import com.htv.smartfarm.identity.authorization.repository.RolePermissionRepository;
import com.htv.smartfarm.identity.authorization.repository.RoleRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent, retroactive fix: ensure EVERY existing SUPERADMIN role holds the wildcard
 * permission {@code *}, so its access token carries {@code scope:"*"} and the gateway's
 * {@code JwtAuthorities.isSuperAdmin} ({@code SCOPE_*}) treats it as root.
 *
 * <p>Verified gap: SUPERADMIN roles created by the old auto-bootstrap had NO permissions, so their
 * tokens had an empty scope and the gateway did not recognise them as super-admin. New admins get
 * the grant in {@link SuperAdminBootstrapService}; this runner repairs ones created earlier so no
 * manual DB edit is needed. Runs on every boot but only writes when a grant is missing.</p>
 */
@Component
public class SuperAdminWildcardGrantRunner implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(SuperAdminWildcardGrantRunner.class);

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final RolePermissionRepository rolePermissions;

    public SuperAdminWildcardGrantRunner(
            RoleRepository roles,
            PermissionRepository permissions,
            RolePermissionRepository rolePermissions
    ) {
        this.roles = roles;
        this.permissions = permissions;
        this.rolePermissions = rolePermissions;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var superAdminRoles = roles.findAll().stream()
                .filter(r -> SuperAdminBootstrapService.SUPERADMIN.equals(r.getCode()))
                .toList();
        if (superAdminRoles.isEmpty()) {
            return;
        }
        PermissionEntity wildcard = permissions
                .findById(SuperAdminBootstrapService.WILDCARD_PERMISSION)
                .orElseGet(() -> permissions.save(new PermissionEntity(
                        SuperAdminBootstrapService.WILDCARD_PERMISSION, "*", "*",
                        "Wildcard permission: grants every scope (root/super-admin)"
                )));
        int granted = 0;
        for (RoleEntity role : superAdminRoles) {
            if (!rolePermissions.existsById(
                    new RolePermissionId(role.getId(), wildcard.getCode()))) {
                rolePermissions.save(new RolePermissionEntity(role, wildcard));
                granted++;
            }
        }
        if (granted > 0) {
            log.info("Granted wildcard '*' to {} existing SUPERADMIN role(s)", granted);
        }
    }
}
