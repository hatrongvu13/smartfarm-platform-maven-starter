package com.htv.smartfarm.identity.config;

import com.htv.smartfarm.identity.user.UserRepository;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * Seeds the baseline roles and their permissions that were previously inserted by
 * {@code schema.sql}. Since JPA now generates the schema (ddl-auto), this seed
 * moved into an idempotent startup runner. It MUST run before {@link BootstrapAdmin}
 * (which assigns ADMIN / PLATFORM_ADMIN to the bootstrap user), hence the low
 * {@link Order} value.
 *
 * Seed set (identical to the former schema.sql):
 *   USER            -> farm:read
 *   ADMIN           -> identity:admin, farm:read
 *   PLATFORM_ADMIN  -> identity:platform
 */
@Configuration(proxyBeanMethods = false)
public class RoleSeeder {

    @Bean
    @Order(0)
    ApplicationRunner seedRoles(UserRepository users) {
        return args -> {
            ensureRole(users, "USER");
            ensureRole(users, "ADMIN");
            ensureRole(users, "PLATFORM_ADMIN");
            ensureRole(users, "SUPERADMIN");
            ensureRole(users, "FARM_OPERATOR");
            grant(users, "USER", "farm:read");
            grant(users, "ADMIN", "identity:admin");
            grant(users, "ADMIN", "farm:read");
            grant(users, "PLATFORM_ADMIN", "identity:platform");
            // SUPERADMIN = the project's first/root admin. It holds the wildcard permission "*",
            // which the token layer expands into every scope the system knows AND which the
            // authorization layer treats as "grants any authority". No per-scope maintenance:
            // new services/scopes are covered automatically.
            grant(users, "SUPERADMIN", "*");
            // Business operator: places orders (saga), manages stock, assigns tasks.
            grant(users, "FARM_OPERATOR", "farm:read");
            grant(users, "FARM_OPERATOR", "orders:write");
            grant(users, "FARM_OPERATOR", "orders:read");
            grant(users, "FARM_OPERATOR", "inventory:write");
            grant(users, "FARM_OPERATOR", "inventory:read");
            grant(users, "FARM_OPERATOR", "tasks:write");
            grant(users, "FARM_OPERATOR", "report:write");
            grant(users, "FARM_OPERATOR", "report:read");
        };
    }

    private static void ensureRole(UserRepository users, String code) {
        if (!users.roleExists(code)) {
            users.createRole(code);
        }
    }

    private static void grant(UserRepository users, String role, String permission) {
        // grantPermission upserts a (role, permission) row; the composite PK makes a
        // repeat save idempotent (same key overwrites), so re-running on startup is safe.
        try {
            users.grantPermission(role, permission);
        } catch (RuntimeException alreadyGranted) {
            // ignore duplicate on restart
        }
    }
}
