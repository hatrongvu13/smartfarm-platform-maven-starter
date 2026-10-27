package com.htv.farmverse.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.htv.farmverse.security.core.TokenType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@ExtendWith(MockitoExtension.class)
class JwtTokenVerifierTest {

    @Mock
    private JwtDecoder decoder;

    private JwtTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new JwtTokenVerifier(decoder);
    }

    @Test
    void shouldMapExplicitUserToken() {
        Jwt jwt = jwt(Map.of(
                "sub", "user-001",
                "tenant_id", "tenant-001",
                "aud", List.of("api-gateway"),
                "scope", "farm:read",
                "roles", List.of("ADMIN"),
                "token_type", "user"
        ));
        when(decoder.decode("token")).thenReturn(jwt);

        var identity = verifier.verify("token");

        assertThat(identity.subject()).isEqualTo("user-001");
        assertThat(identity.tokenType()).isEqualTo(TokenType.USER);
        assertThat(identity.authorities())
                .contains("SCOPE_farm:read", "ROLE_ADMIN");
    }

    @Test
    void shouldMapExplicitServiceToken() {
        Jwt jwt = jwt(Map.of(
                "sub", "gateway",
                "client_id", "gateway",
                "tenant_id", "tenant-001",
                "aud", List.of("farmverse-identity"),
                "scope", "identity:user:read",
                "token_type", "service"
        ));
        when(decoder.decode("token")).thenReturn(jwt);

        var identity = verifier.verify("token");

        assertThat(identity.clientId()).isEqualTo("gateway");
        assertThat(identity.tokenType()).isEqualTo(TokenType.SERVICE);
        assertThat(identity.isService()).isTrue();
    }

    @Test
    void rejectsTokenTypeOmissionEvenWhenClientIdExists() {
        Jwt jwt = jwt(Map.of(
                "sub", "gateway",
                "client_id", "gateway",
                "tenant_id", "tenant-001",
                "aud", List.of("farmverse-identity")
        ));
        when(decoder.decode("token")).thenReturn(jwt);

        assertThatThrownBy(() -> verifier.verify("token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("missing or invalid token_type claim");
    }

    @Test
    void rejectsLegacyTokenWithoutTypeOrClientId() {
        Jwt jwt = jwt(Map.of(
                "sub", "legacy-subject",
                "tenant_id", "tenant-001",
                "aud", List.of("api-gateway")
        ));
        when(decoder.decode("token")).thenReturn(jwt);

        assertThatThrownBy(() -> verifier.verify("token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("missing or invalid token_type claim");
    }

    private Jwt jwt(Map<String, Object> claims) {
        return new Jwt(
                "token-value",
                Instant.parse("2026-09-30T00:00:00Z"),
                Instant.parse("2026-09-30T00:10:00Z"),
                Map.of("alg", "RS256"),
                claims
        );
    }
}
