package com.htv.smartfarm.identity.token.grant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import com.htv.smartfarm.common.contract.TokenClaimContract;
import org.junit.jupiter.api.Test;

class TokenContextContractTest {
    @Test
    void userContextCarriesSessionAuthenticationTimeAndMethods() {
        Instant authenticatedAt = Instant.parse("2026-10-13T08:00:00Z");
        TokenContext context = new TokenContext(
                GrantType.MFA_COMPLETION,
                "user-1",
                "tenant-1",
                "gateway",
                null,
                null,
                Set.of("FARM:READ"),
                Set.of("USER"),
                3,
                7,
                "session-1",
                authenticatedAt,
                List.of("pwd", "totp"),
                Duration.ofMinutes(5)
        );
        assertThat(context.sessionId()).isEqualTo("session-1");
        assertThat(context.authenticatedAt()).isEqualTo(authenticatedAt);
        assertThat(context.authenticationMethods()).containsExactly("pwd", "totp");
    }

    @Test
    void delegatedContextProducesVersionedActorClaim() {
        TokenContext context = new TokenContext(
                GrantType.DELEGATED_SERVICE,
                "svc:gateway",
                "tenant-1",
                "inventory",
                "gateway",
                "user-1",
                Set.of("inventory:read"),
                Set.of(),
                5,
                9,
                null,
                Instant.parse("2026-10-13T08:00:00Z"),
                List.of("client_secret"),
                Duration.ofMinutes(5)
        );
        assertThat(context.delegatedActorClaim())
                .containsEntry(TokenClaimContract.SUBJECT, "user-1")
                .containsEntry(TokenClaimContract.AUTHORIZATION_VERSION, 5L)
                .containsEntry(
                        TokenClaimContract.TENANT_AUTHORIZATION_VERSION,
                        9L
                );
    }

    @Test
    void serviceContextRejectsWildcardScope() {
        assertThatThrownBy(() -> new TokenContext(
                GrantType.SERVICE_CLIENT,
                "svc:gateway",
                "tenant-1",
                "inventory",
                "gateway",
                null,
                Set.of("*"),
                Set.of(),
                0,
                0,
                null,
                Instant.now(),
                List.of("client_secret"),
                Duration.ofMinutes(5)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wildcard");
    }
}
