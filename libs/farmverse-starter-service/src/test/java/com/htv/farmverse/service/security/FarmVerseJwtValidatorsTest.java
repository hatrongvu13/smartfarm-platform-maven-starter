package com.htv.farmverse.service.security;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class FarmVerseJwtValidatorsTest {

    static final String ISSUER = "https://id.example.test";

    private static Jwt token(String issuer, String... audiences) {
        return Jwt.withTokenValue("t").header("alg", "RS256").issuer(issuer).audience(List.of(audiences))
                .subject("u").issuedAt(Instant.now().minusSeconds(5)).expiresAt(Instant.now().plusSeconds(60)).build();
    }

    private static boolean valid(OAuth2TokenValidator<Jwt> validator, Jwt jwt) {
        return !validator.validate(jwt).hasErrors();
    }

    @Test
    void acceptsOnlyItsOwnAudience() {
        var farm = FarmVerseJwtValidators.issuerAndAudience(ISSUER, Set.of(FarmVerseClaims.FARM_AUDIENCE));

        assertThat(valid(farm, token(ISSUER, "farm-service", "identity-service"))).isTrue();
        assertThat(valid(farm, token(ISSUER, "identity-service"))).as("token chỉ cho identity").isFalse();
        assertThat(valid(farm, token(ISSUER, "farmverse-api"))).as("audience chung cũ").isFalse();
        assertThat(valid(farm, token("https://evil.test", "farm-service"))).as("issuer lạ").isFalse();
    }

    @Test
    void legacyAudienceOnlyWhenExplicitlyAcceptedDuringMigration() {
        var migrating = FarmVerseJwtValidators.issuerAndAudience(ISSUER,
                List.of(FarmVerseClaims.FARM_AUDIENCE, FarmVerseClaims.LEGACY_API_AUDIENCE));

        assertThat(valid(migrating, token(ISSUER, "farmverse-api"))).isTrue();
        assertThat(valid(migrating, token(ISSUER, "farm-service"))).isTrue();
        assertThat(valid(migrating, token(ISSUER, "identity-service"))).isFalse();
    }

    @Test
    void emptyAcceptedListIsAConfigurationError() {
        assertThatIllegalArgumentException().isThrownBy(() -> FarmVerseJwtValidators.issuerAndAudience(ISSUER, List.of()));
    }
}
