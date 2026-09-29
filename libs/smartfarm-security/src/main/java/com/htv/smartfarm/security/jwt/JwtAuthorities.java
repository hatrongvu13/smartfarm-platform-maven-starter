package com.htv.smartfarm.security.jwt;

import java.util.*;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

public final class JwtAuthorities {
    /**
     * Wildcard authority carried by the project's root/super-admin: grants ANY scope.
     */
    public static final String WILDCARD = "SCOPE_*";

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
        return Set.copyOf(result);
    }

    /**
     * True when this authority set is the super-admin wildcard (scope "*" -> SCOPE_*).
     */
    public static boolean isSuperAdmin(Collection<String> authorities) {
        return authorities != null && authorities.contains(WILDCARD);
    }
}
