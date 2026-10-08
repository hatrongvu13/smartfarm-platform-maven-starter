package com.htv.smartfarm.identity.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartfarm.identity.authorization")
public record IdentityAuthorizationProperties(
        PermissionSync permissionSync,
        RoleSync roleSync
) {
    public IdentityAuthorizationProperties {
        permissionSync = permissionSync == null ? new PermissionSync(true, true, List.of()) : permissionSync;
        roleSync = roleSync == null ? new RoleSync(true, List.of()) : roleSync;
    }
    public record PermissionSync(boolean enabled, boolean updateExisting, List<PermissionDefinition> permissions) {
        public PermissionSync { permissions = permissions == null ? List.of() : List.copyOf(permissions); }
    }
    public record PermissionDefinition(String code, String resourceType, String action, String description) { }
    public record RoleSync(boolean enabled, List<RoleDefinition> roles) {
        public RoleSync { roles = roles == null ? List.of() : List.copyOf(roles); }
    }
    public record RoleDefinition(String code, String name, boolean systemRole, List<String> permissionCodes) {
        public RoleDefinition { permissionCodes = permissionCodes == null ? List.of() : List.copyOf(permissionCodes); }
    }
}
