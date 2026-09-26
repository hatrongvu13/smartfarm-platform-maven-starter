package com.htv.smartfarm.identity.config;

import com.htv.smartfarm.identity.user.UserRepository;
import com.htv.smartfarm.identity.auth.AuthService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
public class BootstrapAdmin {
    @Bean
    @Order(1)
    ApplicationRunner bootstrap(IdentitySettings settings, UserRepository users, PasswordEncoder encoder) {
        return args -> {
            if (settings.bootstrapEmail() == null || settings.bootstrapEmail().isBlank()) return;
            if (settings.bootstrapTenant() == null || settings.bootstrapTenant().isBlank() || settings.bootstrapPassword() == null)
                throw new IllegalArgumentException("Bootstrap requires tenant and password");
            AuthService.password(settings.bootstrapPassword());
            String email = AuthService.email(settings.bootstrapEmail());
            // Resolve (or create) the bootstrap user, then CONVERGE its roles on every startup.
            // Role assignment must be idempotent: if the admin already existed from a prior run,
            // newly-added roles (e.g. FARM_OPERATOR) must still be granted, not skipped.
            var admin = users.findByEmail(settings.bootstrapTenant(), email)
                    .orElseGet(() -> users.create(settings.bootstrapTenant(), email, encoder.encode(settings.bootstrapPassword())));
            // The project's FIRST admin is the root SUPERADMIN: it holds the wildcard permission
            // and therefore every authority in the system, now and for scopes added later. All
            // OTHER users start with no privilege and must be granted roles by an admin.
            users.assignRole(admin.id(), "SUPERADMIN");
            users.assignRole(admin.id(), "ADMIN");
            users.assignRole(admin.id(), "PLATFORM_ADMIN");
        };
    }
}
