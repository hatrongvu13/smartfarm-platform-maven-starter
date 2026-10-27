package com.htv.farmverse.security.core;

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
                Set.of("api-gateway"),
                TokenType.USER
        );

        assertThat(identity.isUser()).isTrue();
        assertThat(identity.hasAuthority("SCOPE_farm:read"))
                .isTrue();
        assertThat(identity.hasAudience("api-gateway"))
                .isTrue();
    }

    @Test
    void shouldDetectSuperAdmin() {
        SecurityIdentity identity = new SecurityIdentity(
                "root",
                null,
                "platform",
                Set.of("SCOPE_*"),
                Set.of("api-gateway"),
                TokenType.USER
        );

        assertThat(identity.isSuperAdmin()).isTrue();
    }

    @Test
    void serviceTokenMustNotBecomeSuperAdmin() {
        SecurityIdentity identity = new SecurityIdentity(
                "svc:gateway", "gateway", "tenant-001", Set.of("SCOPE_*"),
                Set.of("farmverse-identity"), TokenType.SERVICE);
        assertThat(identity.isSuperAdmin()).isFalse();
    }

    @Test
    void unknownTokenMustNotBecomeSuperAdmin() {
        SecurityIdentity identity = new SecurityIdentity(
                "legacy", null, "tenant-001", Set.of("SCOPE_*"),
                Set.of("api-gateway"), TokenType.UNKNOWN);
        assertThat(identity.isSuperAdmin()).isFalse();
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
