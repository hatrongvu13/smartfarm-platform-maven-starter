package com.htv.farmverse.service.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Đổi access token thành authority cho {@code @PreAuthorize}:
 * <ul>
 *   <li>claim {@code perms}: mỗi mã thành một authority, ví dụ {@code hasAuthority('livestock:animal:read')}</li>
 *   <li>claim {@code roles}: thành {@code ROLE_<code>}, ví dụ {@code hasRole('SUPER_ADMIN')}</li>
 *   <li>claim {@code scope}: giữ cách mặc định của Spring, {@code SCOPE_<scope>}</li>
 * </ul>
 */
public final class FarmVerseJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>(scopes.convert(jwt));
        for (String permission : list(jwt, FarmVerseClaims.PERMISSIONS)) {
            authorities.add(new SimpleGrantedAuthority(permission));
        }
        for (String role : list(jwt, FarmVerseClaims.ROLES)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private static List<String> list(Jwt jwt, String claim) {
        List<String> values = jwt.getClaimAsStringList(claim);
        return values == null ? List.of() : values;
    }
}
