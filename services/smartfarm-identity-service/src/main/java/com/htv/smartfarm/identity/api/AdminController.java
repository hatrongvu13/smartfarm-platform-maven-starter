package com.htv.smartfarm.identity.api;

import com.htv.smartfarm.identity.oauth2.AuthService;
import com.htv.smartfarm.identity.user.UserRepository;

import java.util.*;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuthService auth;

    public AdminController(UserRepository users, PasswordEncoder encoder, AuthService auth) {
        this.users = users;
        this.encoder = encoder;
        this.auth = auth;
    }

    public record CreateUser(String email, String password) {
    }

    public record RoleRequest(String role) {
    }

    public record PermissionRequest(String permission) {
    }

    /** List every role with the permissions it grants. Helps an admin decide what to assign. */
    @GetMapping("/roles")
    public Map<String, Object> listRoles() {
        var roles = new java.util.LinkedHashMap<String, Object>();
        for (String role : users.allRoles()) {
            roles.put(role, users.permissionsOf(role));
        }
        return Map.of("roles", roles);
    }

    /** Every concrete permission (scope) the system knows — the vocabulary for granting. */
    @GetMapping("/permissions")
    public Map<String, Object> listPermissions() {
        return Map.of("permissions", users.allPermissions());
    }

    /** Every user in the caller's tenant with their assigned roles + status (for the admin user list). */
    @GetMapping("/users")
    public Map<String, Object> listUsers(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("users", users.listByTenant(jwt.getClaimAsString("tenant_id")));
    }

    /** The roles and effective scopes of one user in the caller's tenant. */
    @GetMapping("/users/{id}/roles")
    public Map<String, Object> userRoles(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        inTenant(jwt, id);
        return Map.of("userId", id, "roles", users.roles(id), "scopes", users.scopes(id));
    }

    @Transactional
    @PostMapping("/users")
    public Map<String, String> create(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateUser r) {
        AuthService.password(r.password());
        try {
            var u = users.create(jwt.getClaimAsString("tenant_id"), AuthService.email(r.email()), encoder.encode(r.password()));
            users.assignRole(u.id(), "USER");
            return Map.of("userId", u.id());
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account already exists");
        }
    }

    private UserRepository.User inTenant(Jwt jwt, String id) {
        var user = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!user.tenantId().equals(jwt.getClaimAsString("tenant_id")))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return user;
    }

    @PutMapping("/users/{id}/roles/{role}")
    public ResponseEntity<Void> role(@AuthenticationPrincipal Jwt jwt, @PathVariable String id, @PathVariable String role) {
        inTenant(jwt, id);
        if (role.equals("PLATFORM_ADMIN"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform role cannot be delegated via tenant API");
        if (role.equals("SUPERADMIN"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Super-admin cannot be delegated; it is the project root admin only");
        if (!users.roleExists(role)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role");
        try {
            users.assignRole(id, role);
        } catch (DuplicateKeyException ignored) {
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{id}/roles/{role}")
    public ResponseEntity<Void> removeRole(@AuthenticationPrincipal Jwt jwt, @PathVariable String id, @PathVariable String role) {
        inTenant(jwt, id);
        if (role.equals("PLATFORM_ADMIN"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform role cannot be modified via tenant API");
        if (id.equals(jwt.getSubject()) && role.equals("ADMIN"))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot remove own admin role");
        users.removeRole(id, role);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{id}/disable")
    public ResponseEntity<Void> disable(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        inTenant(jwt, id);
        if (id.equals(jwt.getSubject())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot disable self");
        auth.disable(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('SCOPE_identity:platform')")
    @PostMapping("/roles/{role}")
    public ResponseEntity<Void> createRole(@PathVariable String role) {
        if (!role.matches("[A-Z][A-Z0-9_]{1,39}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role");
        try {
            users.createRole(role);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Role exists");
        }
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PreAuthorize("hasAuthority('SCOPE_identity:platform')")
    @PutMapping("/roles/{role}/permissions/{permission}")
    public ResponseEntity<Void> permission(@PathVariable String role, @PathVariable String permission) {
        if (!users.roleExists(role) || !permission.matches("[a-z][a-z0-9:_-]{1,79}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role or permission");
        try {
            users.grantPermission(role, permission);
        } catch (DuplicateKeyException ignored) {
        }
        return ResponseEntity.noContent().build();
    }
}
