package com.htv.farmverse.service.security;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

class FarmVerseJwtAuthenticationConverterTest {

    @Test
    void mapsPermissionsRolesAndScopes() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256")
                .subject("user-1")
                .claim("scope", "openid farmverse.api")
                .claim(FarmVerseClaims.PERMISSIONS, List.of("farm:farm:read"))
                .claim(FarmVerseClaims.ROLES, List.of("SUPER_ADMIN"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .build();

        var authorities = new FarmVerseJwtAuthenticationConverter().convert(jwt).getAuthorities()
                .stream().map(GrantedAuthority::getAuthority).toList();

        assertThat(authorities).contains(
                "farm:farm:read", "ROLE_SUPER_ADMIN", "SCOPE_openid", "SCOPE_farmverse.api");
    }

    @Test
    void tokenWithoutFarmVerseClaimsHasOnlyScopes() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("svc")
                .claim("scope", "farmverse.api")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .build();

        assertThat(new FarmVerseJwtAuthenticationConverter().convert(jwt).getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("SCOPE_farmverse.api");
    }
}
