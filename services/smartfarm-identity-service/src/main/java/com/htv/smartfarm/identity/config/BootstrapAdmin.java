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
            if (users.findByEmail(settings.bootstrapTenant(), email).isPresent()) return;
            var admin = users.create(settings.bootstrapTenant(), email, encoder.encode(settings.bootstrapPassword()));
            users.assignRole(admin.id(), "ADMIN");
            users.assignRole(admin.id(), "PLATFORM_ADMIN");
        };
    }
}
