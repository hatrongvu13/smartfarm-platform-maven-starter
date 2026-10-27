package com.htv.farmverse.identity.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "farmverse.identity.authorization")
public record IdentityAuthorizationProperties(
        PermissionSync permissionSync,
        RoleSync roleSync
) {

    private static final PermissionSync DEFAULT_PERMISSION_SYNC =
            new PermissionSync(
                    true,
                    true,
                    List.of()
            );

    private static final RoleSync DEFAULT_ROLE_SYNC =
            new RoleSync(
                    true,
                    List.of()
            );

    public IdentityAuthorizationProperties {
        permissionSync = permissionSync == null
                ? DEFAULT_PERMISSION_SYNC
                : permissionSync;

        roleSync = roleSync == null
                ? DEFAULT_ROLE_SYNC
                : roleSync;
    }

    public record PermissionSync(
            boolean enabled,
            boolean updateExisting,
            List<PermissionDefinition> permissions
    ) {

        public PermissionSync {
            permissions = immutableList(permissions);
        }
    }

    public record PermissionDefinition(
            String code,
            String resourceType,
            String action,
            String description
    ) {
    }

    public record RoleSync(
            boolean enabled,
            List<RoleDefinition> roles
    ) {

        public RoleSync {
            roles = immutableList(roles);
        }
    }

    public record RoleDefinition(
            String code,
            String name,
            boolean systemRole,
            List<String> permissionCodes
    ) {

        public RoleDefinition {
            permissionCodes = immutableList(permissionCodes);
        }
    }

    private static <T> List<T> immutableList(List<T> values) {
        return values == null
                ? List.of()
                : List.copyOf(values);
    }
}