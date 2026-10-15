package com.htv.smartfarm.identity.token;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Duration;
import java.util.Set;
import com.htv.smartfarm.identity.token.grant.GrantType;
import com.htv.smartfarm.identity.token.grant.TokenContext;
import org.junit.jupiter.api.Test;

class TokenServiceWildcardGuardTest {
    @Test
    void rejectsRawWildcardForServiceToken() {
        assertThatThrownBy(() -> serviceContext(Set.of("*")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("wildcard");
    }

    @Test
    void rejectsAuthorityWildcardForServiceToken() {
        assertThatThrownBy(() -> serviceContext(Set.of("SCOPE_*")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("wildcard");
    }

    @Test
    void acceptsExplicitServiceScopes() {
        serviceContext(Set.of("inventory:read", "inventory:write"));
    }

    @Test
    void rejectsTokenLifetimesLongerThanFifteenMinutes() {
        assertThatThrownBy(() -> new TokenContext(
                GrantType.SERVICE_CLIENT,
                "svc:gateway",
                "tenant",
                "audience",
                "gateway",
                null,
                Set.of("inventory:read"),
                Set.of(),
                0,
                0,
                null,
                null,
                java.util.List.of(),
                Duration.ofMinutes(16)
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("15 minutes");
    }

    private static TokenContext serviceContext(Set<String> scopes) {
        return new TokenContext(
                GrantType.SERVICE_CLIENT,
                "svc:gateway",
                "tenant",
                "audience",
                "gateway",
                null,
                scopes,
                Set.of(),
                0,
                0,
                null,
                null,
                java.util.List.of(),
                Duration.ofMinutes(5)
        );
    }
}
