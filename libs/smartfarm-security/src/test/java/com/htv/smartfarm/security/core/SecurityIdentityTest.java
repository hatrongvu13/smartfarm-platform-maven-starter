package com.htv.smartfarm.security.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

class SecurityIdentityTest {

    @Test
    void shouldCopyAuthoritiesAndAudiences() {
        SecurityIdentity identity = new SecurityIdentity(
                "user-001",
                null,
                "tenant-001",
                Set.of("SCOPE_farm:read"),
                Set.of("smartfarm-gateway"),
                TokenType.USER
        );

        assertThat(identity.isUser()).isTrue();
        assertThat(identity.hasAuthority("SCOPE_farm:read"))
                .isTrue();
        assertThat(identity.hasAudience("smartfarm-gateway"))
                .isTrue();
    }

    @Test
    void shouldDetectSuperAdmin() {
        SecurityIdentity identity = new SecurityIdentity(
                "root",
                null,
                "platform",
                Set.of("SCOPE_*"),
                Set.of("smartfarm-gateway"),
                TokenType.USER
        );

        assertThat(identity.isSuperAdmin()).isTrue();
    }

    @Test
    void shouldRejectBlankSubject() {
        assertThatThrownBy(() -> new SecurityIdentity(
                " ",
                null,
                "tenant-001",
                Set.of(),
                Set.of(),
                TokenType.USER
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
