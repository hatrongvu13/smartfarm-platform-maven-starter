package com.htv.smartfarm.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtAuthoritiesTest {
    @Test
    void userWildcardExpandsToEveryKnownConcreteScope() {
        Jwt jwt = jwt("*");
        var values = JwtAuthorities.authorities(jwt);
        assertThat(values).contains(JwtAuthorities.WILDCARD);
        assertThat(values).contains("SCOPE_orders:saga:read", "SCOPE_orders:saga:admin");
        assertThat(values).doesNotContain("SCOPE_...", "SCOPE_x", "SCOPE_identity.principal.read");
    }

    @Test
    void canonicalAuthorityRejectsUnknownAndNormalizesKnownScope() {
        assertThat(JwtAuthorities.canonicalAuthority("SCOPE_ORDERS:SAGA:READ"))
                .isEqualTo("SCOPE_orders:saga:read");
        assertThat(JwtAuthorities.canonicalAuthority("SCOPE_not:registered")).isNull();
    }

    private Jwt jwt(String scope) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(300),
                Map.of("alg", "RS256"), Map.of("sub", "root", "tenant_id", "system",
                "aud", List.of("smartfarm-gateway"), "scope", scope, "token_type", "user"));
    }
}
