package com.htv.smartfarm.security.issuer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RsaJwtIssuerTest {

    private static RSAPublicKey publicKey;
    private static RSAPrivateKey privateKey;

    private RsaJwtIssuer issuer;

    @BeforeAll
    static void createKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        publicKey = (RSAPublicKey) keyPair.getPublic();
        privateKey = (RSAPrivateKey) keyPair.getPrivate();
    }

    @BeforeEach
    void setUp() {
        issuer = new RsaJwtIssuer(
                publicKey,
                privateKey,
                "test-key",
                "https://identity.smartfarm.test",
                Duration.ofMinutes(5),
                Clock.fixed(
                        Instant.parse("2026-09-30T00:00:00Z"),
                        ZoneOffset.UTC
                )
        );
    }

    @Test
    void shouldIssueUserTokenWithExplicitType() throws Exception {
        String token = issuer.issueUserToken(
                "user-001",
                "tenant-001",
                "smartfarm-gateway",
                List.of("farm:read", "farm:read"),
                List.of("ADMIN")
        );

        var claims = SignedJWT.parse(token).getJWTClaimsSet();

        assertThat(claims.getSubject()).isEqualTo("user-001");
        assertThat(claims.getStringClaim("token_type"))
                .isEqualTo("user");
        assertThat(claims.getStringClaim("tenant_id"))
                .isEqualTo("tenant-001");
        assertThat(claims.getStringClaim("scope"))
                .isEqualTo("farm:read");
        assertThat(claims.getStringListClaim("roles"))
                .containsExactly("ADMIN");
        assertThat(claims.getAudience())
                .containsExactly("smartfarm-gateway");
    }

    @Test
    void shouldIssueServiceTokenWithClientIdentity() throws Exception {
        String token = issuer.issueServiceToken(
                "gateway",
                "tenant-001",
                "smartfarm-identity",
                List.of("identity:user:read")
        );

        var claims = SignedJWT.parse(token).getJWTClaimsSet();

        assertThat(claims.getSubject()).isEqualTo("gateway");
        assertThat(claims.getStringClaim("client_id"))
                .isEqualTo("gateway");
        assertThat(claims.getStringClaim("token_type"))
                .isEqualTo("service");
        assertThat(claims.getStringListClaim("roles"))
                .isEmpty();
    }

    @Test
    void publicJwksShouldNotContainPrivateKey() {
        String jwks = issuer.publicJwksJson();

        assertThat(jwks).contains("test-key");
        assertThat(jwks).doesNotContain("\"d\"");
    }

    @Test
    void shouldRejectLifetimeLongerThanMaximum() {
        assertThatThrownBy(() -> new RsaJwtIssuer(
                publicKey,
                privateKey,
                "test-key",
                "https://identity.smartfarm.test",
                Duration.ofMinutes(16)
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
