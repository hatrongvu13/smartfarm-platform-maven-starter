package com.htv.smartfarm.security.jwt;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/** Canonical conversion from bare JWT scope codes to Spring SCOPE_* authorities. */
public final class JwtAuthorities {

    public static final String WILDCARD_SCOPE = "*";
    public static final String WILDCARD = "SCOPE_*";

    /** Concrete bare scope codes. Wildcard and invalid placeholders never belong here. */
    public static final Set<String> KNOWN_SCOPES = Set.of(
            "farm:read",
            "finance:read",
            "finance:write",
            "health:read",
            "health:write",
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
            "tasks:write"
    );

    private JwtAuthorities() {
    }

    public static Set<String> authorities(Jwt jwt) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        Collection<org.springframework.security.core.GrantedAuthority> converted =
                new JwtGrantedAuthoritiesConverter().convert(jwt);
        if (converted != null) {
            converted.stream()
                    .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                    .map(JwtAuthorities::canonicalAuthority)
                    .filter(value -> value != null)
                    .forEach(result::add);
        }
        Object roles = jwt.getClaims().get("roles");
        if (roles instanceof Collection<?> values) {
            for (Object role : values) {
                if (role instanceof String value && value.matches("[A-Za-z0-9_:-]{1,64}")) {
                    result.add("ROLE_" + value);
                }
            }
        }
        // Spring's hasAuthority compares exact strings. Expand only USER wildcard tokens into
        // the finite concrete catalog while retaining SCOPE_* as the root marker.
        if (result.contains(WILDCARD)) {
            KNOWN_SCOPES.stream().map(JwtAuthorities::toAuthority).forEach(result::add);
        }
        return Set.copyOf(result);
    }

    public static String toAuthority(String bareScope) {
        if (bareScope == null || bareScope.isBlank()) return null;
        String value = bareScope.trim();
        if (WILDCARD_SCOPE.equals(value) || WILDCARD.equalsIgnoreCase(value)) return WILDCARD;
        if (value.regionMatches(true, 0, "SCOPE_", 0, 6)) value = value.substring(6);
        value = value.toLowerCase(java.util.Locale.ROOT);
        return KNOWN_SCOPES.contains(value) ? "SCOPE_" + value : null;
    }

    public static String canonicalAuthority(String authority) {
        if (authority == null || authority.isBlank()) return null;
        String value = authority.trim();
        if (value.startsWith("ROLE_")) return value;
        return toAuthority(value);
    }

    public static boolean isSuperAdmin(Collection<String> authorities) {
        return authorities != null && authorities.contains(WILDCARD);
    }
}
