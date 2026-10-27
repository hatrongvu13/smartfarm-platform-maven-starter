package com.htv.farmverse.identity.web;

import java.util.List;
import java.util.Set;

import com.htv.farmverse.identity.authorization.Permission;
import com.htv.farmverse.identity.authorization.Role;
import com.htv.farmverse.identity.authorization.RoleAdministrationService;
import com.htv.farmverse.service.security.FarmVerseClaims;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Role của tenant (theo token) và danh mục permission của nền tảng. */
@RestController
@RequiredArgsConstructor
public class RoleController {

    private final RoleAdministrationService administration;

    // ---- /roles ----

    @GetMapping("/roles")
    @PreAuthorize(Can.READ_ROLES)
    public List<RoleView> roles(@AuthenticationPrincipal Jwt jwt) {
        return administration.listRoles(tenant(jwt)).stream().map(RoleView::of).toList();
    }

    @GetMapping("/roles/{code}")
    @PreAuthorize(Can.READ_ROLES)
    public RoleView role(@AuthenticationPrincipal Jwt jwt, @PathVariable String code) {
        return RoleView.of(administration.getRole(tenant(jwt), code));
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Can.MANAGE_ROLES)
    public RoleView createRole(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RoleInput body) {
        return RoleView.of(administration.createRole(tenant(jwt), body.code(), body.name(), body.description(),
                body.permissions()));
    }

    @PutMapping("/roles/{code}")
    @PreAuthorize(Can.MANAGE_ROLES)
    public RoleView updateRole(@AuthenticationPrincipal Jwt jwt, @PathVariable String code,
                               @Valid @RequestBody RoleInput body) {
        return RoleView.of(administration.updateRole(tenant(jwt), code, body.name(), body.description(),
                body.permissions()));
    }

    @DeleteMapping("/roles/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Can.MANAGE_ROLES)
    public void deleteRole(@AuthenticationPrincipal Jwt jwt, @PathVariable String code) {
        administration.deleteRole(tenant(jwt), code);
    }

    // ---- /permissions ----

    @GetMapping("/permissions")
    @PreAuthorize(Can.READ_ROLES)
    public List<PermissionView> permissions() {
        return administration.listPermissions().stream().map(PermissionView::of).toList();
    }

    @PostMapping("/permissions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Can.MANAGE_PERMISSIONS)
    public PermissionView createPermission(@Valid @RequestBody PermissionInput body) {
        return PermissionView.of(administration.createPermission(body.code(), body.resourceType(), body.action(),
                body.description()));
    }

    @PutMapping("/permissions/{code}")
    @PreAuthorize(Can.MANAGE_PERMISSIONS)
    public PermissionView updatePermission(@PathVariable String code, @RequestBody PermissionDescription body) {
        return PermissionView.of(administration.updatePermission(code, body.description()));
    }

    @DeleteMapping("/permissions/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Can.MANAGE_PERMISSIONS)
    public void deletePermission(@PathVariable String code) {
        administration.deletePermission(code);
    }

    private static String tenant(Jwt jwt) {
        return jwt.getClaimAsString(FarmVerseClaims.TENANT_ID);
    }

    // ---- DTO ----

    public record RoleInput(@NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,39}") String code,
                            @NotBlank @Size(max = 120) String name,
                            @Size(max = 255) String description,
                            Set<String> permissions) {
    }

    public record RoleView(String code, String name, String description, boolean systemOwned, List<String> permissions) {
        static RoleView of(Role role) {
            return new RoleView(role.getCode(), role.getName(), role.getDescription(), role.isSystemOwned(),
                    role.getPermissions().stream().map(Permission::getCode).sorted().toList());
        }
    }

    public record PermissionInput(@NotBlank @Pattern(regexp = "[a-z][a-z0-9-]*(:[a-z0-9-]+){1,3}") String code,
                                  @NotBlank String resourceType,
                                  @NotBlank String action,
                                  @Size(max = 255) String description) {
    }

    public record PermissionDescription(@Size(max = 255) String description) {
    }

    public record PermissionView(String code, String resourceType, String action, String description,
                                 boolean systemOwned) {
        static PermissionView of(Permission permission) {
            return new PermissionView(permission.getCode(), permission.getResourceType(), permission.getAction(),
                    permission.getDescription(), permission.isSystemOwned());
        }
    }
}
