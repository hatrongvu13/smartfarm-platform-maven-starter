package com.htv.smartfarm.identity.token;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import org.junit.jupiter.api.Test;

class TokenServiceWildcardGuardTest {
    @Test
    void rejectsRawWildcardForServiceToken() {
        assertThatThrownBy(() -> TokenService.validateServiceScopes(List.of("*")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("wildcard");
    }

    @Test
    void rejectsAuthorityWildcardForServiceToken() {
        assertThatThrownBy(() -> TokenService.validateServiceScopes(List.of("SCOPE_*")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("wildcard");
    }

    @Test
    void acceptsExplicitServiceScopes() {
        TokenService.validateServiceScopes(List.of("inventory:read", "inventory:write"));
    }
}
