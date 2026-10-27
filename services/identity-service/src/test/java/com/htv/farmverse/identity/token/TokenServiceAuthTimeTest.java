package com.htv.farmverse.identity.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class TokenServiceAuthTimeTest {

    @Test
    void encodesAuthenticatedAtAsNumericDate() throws Exception {
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
        var service = new TokenService(
                settings,
                new DefaultResourceLoader(),
                Clock.systemUTC()
        );
        Instant authenticatedAt = Instant.parse("2026-10-15T08:00:00Z");
        var context = new TokenContext(
                GrantType.SERVICE_CLIENT,
                "svc:gateway",
                "farm-demo",
                "farmverse-identity",
                "gateway",
                null,
                Set.of("identity:principal:read"),
                Set.of(),
                0,
                0,
                null,
                authenticatedAt,
                List.of("client_secret"),
                Duration.ofMinutes(5)
        );

        String token = new NimbusUserTokenSigner(
                service,
                Clock.systemUTC()
        ).sign(context).value();
        var decoded = service.decoder().decode(token);
        Object authTime = decoded.getClaim(
                TokenClaimContract.AUTHENTICATION_TIME
        );

        assertThat(authTime).isInstanceOf(Number.class);
        assertThat(((Number) authTime).longValue())
                .isEqualTo(authenticatedAt.getEpochSecond());
        assertThat(((Number) decoded.getClaim(
                TokenClaimContract.CONTRACT_VERSION_CLAIM
        )).intValue()).isEqualTo(
                TokenClaimContract.CURRENT_CONTRACT_VERSION
        );
    }

    @Test
    void encodesSubjectAndTenantAuthorizationVersionsFromGrantContext()
            throws Exception {
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
        var service = new TokenService(
                settings,
                new DefaultResourceLoader(),
                Clock.systemUTC()
        );
        var context = new TokenContext(
                GrantType.USER_LOGIN,
                "subject-1",
                "tenant-1",
                "api-gateway",
                null,
                null,
                Set.of("identity:principal:read"),
                Set.of("ADMIN"),
                11,
                22,
                null,
                null,
                List.of("password"),
                Duration.ofMinutes(5)
        );

        String token = new NimbusUserTokenSigner(
                service,
                Clock.systemUTC()
        ).sign(context).value();
        var decoded = service.decoder().decode(token);

        assertThat(((Number) decoded.getClaim(
                TokenClaimContract.AUTHORIZATION_VERSION
        )).longValue()).isEqualTo(11);
        assertThat(((Number) decoded.getClaim(
                TokenClaimContract.TENANT_AUTHORIZATION_VERSION
        )).longValue()).isEqualTo(22);
    }
}
