package com.htv.smartfarm.security.jwt;

import java.util.*;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

public final class JwtAuthorities {
    /**
     * Wildcard authority carried by the project's root/super-admin: grants ANY scope.
     */
    public static final String WILDCARD = "SCOPE_*";

    /**
     * The complete set of scope authorities the edge (gateway) enforces via
     * {@code @PreAuthorize("hasAuthority('SCOPE_x')")}. A super-admin token carries scope {@code *},
     * but Spring Security 7 authorizes by comparing authority STRINGS (not object equality), so a
     * bare {@code SCOPE_*} does not satisfy {@code hasAuthority('SCOPE_farm:read')}. We therefore
     * expand {@code *} into these concrete authorities so a root token passes every scope gate.
     *
     * <p>KEEP THIS IN SYNC with the gateway controllers: when you add a new
     * {@code hasAuthority('SCOPE_...')} check, add the bare scope here so super-admin keeps working.
     * (Enumerated from the gateway's @PreAuthorize usages.)</p>
     */
    public static final Set<String> KNOWN_SCOPES = Set.of(
        "*",
        "...",
        "farm:read",
        "finance:read",
        "finance:write",
        "health:read",
        "health:write",
        "identity.principal.read",
        "identity:mfa:disable",
        "identity:mfa:enroll",
        "identity:mfa:recovery:regenerate",
        "identity:permission:check",
        "identity:permission:manage",
        "identity:permission:read",
        "identity:platform:manage",
        "identity:principal:impersonate",
        "identity:principal:read",
        "identity:principal:update",
        "identity:role:assign",
        "identity:role:manage",
        "identity:role:read",
        "identity:security:read",
        "identity:tenant:cross",
        "identity:user:account:manage",
        "identity:user:create",
        "identity:user:credential:reset",
        "identity:user:disable",
        "identity:user:mfa:reset",
        "identity:user:profile:manage",
        "identity:user:profile:update",
        "identity:user:read",
        "inventory:read",
        "inventory:write",
        "orders:admin",
        "orders:outbox:admin",
        "orders:read",
        "orders:saga:admin",
        "orders:saga:read",
        "orders:write",
        "platform:read",
        "report:read",
        "report:write",
        "tasks:write",
        "x"
    );

    private JwtAuthorities() {
    }

    public static Set<String> authorities(Jwt jwt) {
        Set<String> result = new HashSet<>();
        var scopes = new JwtGrantedAuthoritiesConverter().convert(jwt);
        if (scopes != null) scopes.forEach(a -> result.add(a.getAuthority()));
        Object roles = jwt.getClaims().get("roles");
        if (roles instanceof Collection<?> list) for (Object role : list) {
            if (role instanceof String s && s.matches("[A-Za-z0-9_:-]{1,64}")) result.add("ROLE_" + s);
        }
        // Super-admin: a wildcard scope "*" (-> SCOPE_*) is expanded to the concrete known scope
        // authorities so plain hasAuthority('SCOPE_x') checks pass. SCOPE_* itself is retained so
        // isSuperAdmin() still recognises the root token.
        if (result.contains(WILDCARD)) {
            for (String scope : KNOWN_SCOPES) {
                result.add("SCOPE_" + scope);
            }
        }
        return Set.copyOf(result);
    }

    /**
     * True when this authority set is the super-admin wildcard (scope "*" -> SCOPE_*).
     */
    public static boolean isSuperAdmin(Collection<String> authorities) {
        return authorities != null && authorities.contains(WILDCARD);
    }
}
