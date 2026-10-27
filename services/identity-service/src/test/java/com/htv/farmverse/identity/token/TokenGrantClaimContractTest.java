package com.htv.farmverse.identity.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.htv.farmverse.common.contract.TokenClaimContract;
import com.htv.farmverse.identity.config.IdentitySettings;
import com.htv.farmverse.identity.token.grant.GrantType;
import com.htv.farmverse.identity.token.grant.NimbusUserTokenSigner;
import com.htv.farmverse.identity.token.grant.TokenContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class TokenGrantClaimContractTest {

    private static final Instant AUTHENTICATED_AT =
            Instant.parse("2026-10-16T00:00:00Z");

    private static TokenService tokenService;
    private static NimbusUserTokenSigner signer;
    private static JwtDecoder contractDecoder;

    @BeforeAll
    static void createSigner() throws Exception {
        var settings = new IdentitySettings(
                "http://localhost:8092",
                "api-gateway",
                "farmverse-identity",
                "test-key",
                "classpath:.local/identity-private.pem",
                "classpath:.local/identity-public.pem",
                Duration.ofMinutes(5),
                Duration.ofDays(14),
                5,
                Duration.ofMinutes(15),
                false,
                "",
                "farm-demo"
        );
        tokenService = new TokenService(
                settings,
                new DefaultResourceLoader(),
                Clock.systemUTC()
        );
        signer = new NimbusUserTokenSigner(tokenService, Clock.systemUTC());
        var signingKey = (RSAKey) JWKSet.parse(tokenService.jwks())
                .getKeys()
                .get(0);
        contractDecoder = NimbusJwtDecoder
                .withPublicKey(signingKey.toRSAPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        ((NimbusJwtDecoder) contractDecoder).setJwtValidator(
                JwtValidators.createDefault()
        );
    }

    @Test
    void userLoginCarriesVersionedIdentityAndAuthorizationClaims() {
        Jwt token = issue(GrantType.USER_LOGIN, "user-1", "tenant-1",
                "api-gateway", null, null, Set.of("farm:read"),
                Set.of("FARM_ADMIN"), 11, 22, null,
                AUTHENTICATED_AT, List.of("password"));

        assertCommonIdentityClaims(
                token, "user-1", "tenant-1", "api-gateway", "user"
        );
        assertThat(token.getClaimAsString(TokenClaimContract.SCOPE))
                .isEqualTo("farm:read");
        assertThat(token.getClaimAsStringList(TokenClaimContract.ROLES))
                .containsExactly("FARM_ADMIN");
        assertThat(token.getClaims().get(
                TokenClaimContract.AUTHORIZATION_VERSION
        ))
                .isEqualTo(11L);
        assertThat(token.getClaims().get(
                TokenClaimContract.TENANT_AUTHORIZATION_VERSION
        )).isEqualTo(22L);
    }

    @Test
    void bootstrapGrantCarriesTheVersionedUserTokenContract() {
        Jwt token = issue(GrantType.BOOTSTRAP, "first-admin", "tenant-1",
                "api-gateway", null, null, Set.of("identity:platform"),
                Set.of("SUPERADMIN"), 0, 0, null, AUTHENTICATED_AT,
                List.of("password"));

        assertCommonIdentityClaims(
                token, "first-admin", "tenant-1", "api-gateway", "user"
        );
        assertThat(token.getClaimAsString(TokenClaimContract.SCOPE))
                .isEqualTo("identity:platform");
        assertThat(token.getClaimAsStringList(TokenClaimContract.ROLES))
                .containsExactly("SUPERADMIN");
    }

    @Test
    void mfaCompletionCarriesAuthenticationTimeAndMethods() {
        Jwt token = issue(GrantType.MFA_COMPLETION, "user-1", "tenant-1",
                "api-gateway", null, null, Set.of("farm:read"),
                Set.of("USER"), 2, 4, "session-mfa", AUTHENTICATED_AT,
                List.of("password", "totp"));

        assertCommonIdentityClaims(
                token, "user-1", "tenant-1", "api-gateway", "user"
        );
        assertThat(token.getClaims().get(
                TokenClaimContract.AUTHENTICATION_TIME
        ))
                .isEqualTo(AUTHENTICATED_AT.getEpochSecond());
        assertThat(token.getClaimAsStringList(
                TokenClaimContract.AUTHENTICATION_METHODS
        )).containsExactly("password", "totp");
    }

    @Test
    void refreshRotationCarriesTheNewSessionIdentifier() {
        Jwt token = issue(GrantType.REFRESH_ROTATION, "user-1", "tenant-1",
                "api-gateway", null, null, Set.of("farm:read"),
                Set.of("USER"), 3, 5, "session-refreshed", AUTHENTICATED_AT,
                List.of("password"));

        assertCommonIdentityClaims(
                token, "user-1", "tenant-1", "api-gateway", "user"
        );
        assertThat(token.getClaimAsString(TokenClaimContract.SESSION_ID))
                .isEqualTo("session-refreshed");
    }

    @Test
    void machineClientGrantHasClientIdentityAndNoUserAuthorizationVersions() {
        Jwt token = issue(GrantType.SERVICE_CLIENT, "svc:order", "tenant-1",
                "farmverse-inventory", "order", null,
                Set.of("inventory:read"), Set.of(), 0, 0, null, null,
                List.of("client_secret"));

        assertCommonIdentityClaims(
                token, "svc:order", "tenant-1", "farmverse-inventory", "service"
        );
        assertThat(token.getClaimAsString(TokenClaimContract.CLIENT_ID))
                .isEqualTo("order");
        assertThat(token.getClaims().get(
                TokenClaimContract.AUTHORIZATION_VERSION
        ))
                .isNull();
        assertThat(token.getClaims().get(
                TokenClaimContract.TENANT_AUTHORIZATION_VERSION
        )).isNull();
    }

    @Test
    void delegatedGrantCarriesTheSignedActorAndItsVersions() {
        Jwt token = issue(GrantType.DELEGATED_SERVICE, "svc:gateway",
                "tenant-1", "farmverse-order", "gateway", "user-1",
                Set.of("orders:read"), Set.of(), 7, 9, null,
                AUTHENTICATED_AT, List.of("client_secret"));

        assertCommonIdentityClaims(
                token, "svc:gateway", "tenant-1", "farmverse-order", "service"
        );
        assertThat(token.getClaimAsString(TokenClaimContract.CLIENT_ID))
                .isEqualTo("gateway");
        assertThat(token.getClaimAsMap(TokenClaimContract.DELEGATED_ACTOR))
                .containsEntry(TokenClaimContract.SUBJECT, "user-1")
                .containsEntry(TokenClaimContract.AUTHORIZATION_VERSION, 7L)
                .containsEntry(
                        TokenClaimContract.TENANT_AUTHORIZATION_VERSION,
                        9L
                );
    }

    private static Jwt issue(
            GrantType grantType,
            String subject,
            String tenant,
            String audience,
            String clientId,
            String actor,
            Set<String> scopes,
            Set<String> roles,
            long subjectVersion,
            long tenantVersion,
            String sessionId,
            Instant authenticatedAt,
            List<String> authenticationMethods
    ) {
        var context = new TokenContext(
                grantType,
                subject,
                tenant,
                audience,
                clientId,
                actor,
                scopes,
                roles,
                subjectVersion,
                tenantVersion,
                sessionId,
                authenticatedAt,
                authenticationMethods,
                Duration.ofMinutes(5)
        );
        return contractDecoder.decode(signer.sign(context).value());
    }

    private static void assertCommonIdentityClaims(
            Jwt token,
            String subject,
            String tenant,
            String audience,
            String tokenType
    ) {
        assertThat(token.getSubject()).isEqualTo(subject);
        assertThat(token.getClaimAsString(TokenClaimContract.TENANT_ID))
                .isEqualTo(tenant);
        assertThat(token.getAudience()).containsExactly(audience);
        assertThat(token.getClaimAsString(TokenClaimContract.TOKEN_TYPE))
                .isEqualTo(tokenType);
        assertThat(token.getId()).isNotBlank();
        assertThat(token.getIssuedAt()).isNotNull();
        assertThat(token.getNotBefore()).isNotNull();
        assertThat(token.getExpiresAt()).isNotNull();
        assertThat(token.getClaims().get(
                TokenClaimContract.CONTRACT_VERSION_CLAIM
        )).isInstanceOf(Number.class);
        assertThat(((Number) token.getClaims().get(
                TokenClaimContract.CONTRACT_VERSION_CLAIM
        )).intValue()).isEqualTo(TokenClaimContract.CURRENT_CONTRACT_VERSION);
    }
}
