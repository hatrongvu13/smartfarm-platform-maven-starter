package com.htv.smartfarm.security.jwt;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Canonical conversion from JWT scope codes to Spring authorities.
 *
 * Identity owns the permission catalog. This transport library validates only the
 * canonical syntax and must never keep a second hard-coded list of permission codes.
 */
public final class JwtAuthorities {

    public static final String WILDCARD_SCOPE = "*";
    public static final String WILDCARD = "SCOPE_*";

    private static final Pattern PERMISSION_SCOPE_PATTERN = Pattern.compile(
            "[a-z][a-z0-9-]{0,39}(?::[a-z][a-z0-9-]{0,39}){1,5}"
    );
    private static final Pattern ROLE_PATTERN = Pattern.compile(
            "[A-Za-z0-9_:-]{1,64}"
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
                    .filter(java.util.Objects::nonNull)
                    .forEach(result::add);
        }

        Object roles = jwt.getClaims().get("roles");
        if (roles instanceof Collection<?> values) {
            for (Object role : values) {
                if (role instanceof String value && ROLE_PATTERN.matcher(value).matches()) {
                    result.add("ROLE_" + value);
                }
            }
        }
        return Set.copyOf(result);
    }

    public static String toAuthority(String bareScope) {
        if (bareScope == null || bareScope.isBlank()) return null;
        String value = bareScope.trim();
        if (WILDCARD_SCOPE.equals(value) || WILDCARD.equalsIgnoreCase(value)) {
            return WILDCARD;
        }
        if (value.regionMatches(true, 0, "SCOPE_", 0, 6)) {
            value = value.substring(6);
        }
        value = value.toLowerCase(Locale.ROOT);
        return PERMISSION_SCOPE_PATTERN.matcher(value).matches()
                ? "SCOPE_" + value
                : null;
    }

    public static String canonicalAuthority(String authority) {
        if (authority == null || authority.isBlank()) return null;
        String value = authority.trim();
        if (value.startsWith("ROLE_")) {
            String role = value.substring(5);
            return ROLE_PATTERN.matcher(role).matches() ? value : null;
        }
        return toAuthority(value);
    }

    public static boolean isSuperAdmin(Collection<String> authorities) {
        return authorities != null && authorities.contains(WILDCARD);
    }
}
