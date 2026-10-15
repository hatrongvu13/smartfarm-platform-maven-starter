package com.htv.smartfarm.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.htv.smartfarm.common.contract.TokenClaimContract;
import com.htv.smartfarm.security.config.SmartFarmSecurityProperties;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class SmartFarmJwtValidatorTest {

    private static final String ISSUER = "https://identity.smartfarm.test";

    private final SmartFarmJwtValidator validator = new SmartFarmJwtValidator(
            new SmartFarmSecurityProperties.Jwt(
                    URI.create(ISSUER),
                    URI.create(ISSUER + "/.well-known/jwks.json"),
                    Set.of("smartfarm-order"),
                    false
            )
    );

    @Test
    void acceptsConfiguredAudience() {
        assertThat(validator.validate(jwt(List.of("smartfarm-order"))).hasErrors())
                .isFalse();
    }

    @Test
    void acceptsCurrentIdentityClaimContractVersion() {
        Jwt token = jwt(List.of("smartfarm-order"));
        Map<String, Object> claims = new HashMap<>(token.getClaims());
        claims.put(
                TokenClaimContract.CONTRACT_VERSION_CLAIM,
                TokenClaimContract.CURRENT_CONTRACT_VERSION
        );

        assertThat(validator.validate(new Jwt(
                token.getTokenValue(),
                token.getIssuedAt(),
                token.getExpiresAt(),
                token.getHeaders(),
                claims
        )).hasErrors()).isFalse();
    }

    @Test
    void rejectsUnsupportedIdentityClaimContractVersion() {
        Jwt token = jwt(List.of("smartfarm-order"));
        Map<String, Object> claims = new HashMap<>(token.getClaims());
        claims.put(TokenClaimContract.CONTRACT_VERSION_CLAIM, 2);
        Jwt versioned = new Jwt(
                token.getTokenValue(),
                token.getIssuedAt(),
                token.getExpiresAt(),
                token.getHeaders(),
                claims
        );

        var result = validator.validate(versioned);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors())
                .anySatisfy(error -> assertThat(error.getDescription())
                        .isEqualTo("unsupported_identity_claims_version"));
    }

    @Test
    void rejectsGatewayAudienceAtOrderResourceServer() {
        var result = validator.validate(jwt(List.of("smartfarm-gateway")));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors())
                .anySatisfy(error -> assertThat(error.getDescription())
                        .isEqualTo("audience_mismatch"));
    }

    @Test
    void rejectsMissingAudience() {
        assertThat(validator.validate(jwt(List.of())).hasErrors()).isTrue();
    }

    @Test
    void acceptsTokenWhenOneAudienceMatchesConfiguredSet() {
        assertThat(validator.validate(jwt(List.of(
                "smartfarm-gateway",
                "smartfarm-order"
        ))).hasErrors()).isFalse();
    }

    private Jwt jwt(List<String> audiences) {
        Instant now = Instant.now();
        return new Jwt(
                "token",
                now.minusSeconds(5),
                now.plusSeconds(300),
                Map.of("alg", "RS256"),
                Map.of(
                        "iss", ISSUER,
                        "sub", "svc:gateway",
                        "tenant_id", "tenant-001",
                        "aud", audiences
                )
        );
    }
}
