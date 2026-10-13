package com.htv.smartfarm.identity.token.session.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RefreshTokenEntityTest {
    private static final Instant NOW = Instant.parse("2026-10-13T08:00:00Z");

    @Test
    void tokenCanBeConsumedOnlyOnce() {
        var token = new RefreshTokenEntity(
                "token-1", "session-1", "a".repeat(64),
                NOW, NOW.plusSeconds(3600)
        );
        token.consume(NOW.plusSeconds(1), "token-2");
        assertThat(token.getConsumedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(token.getReplacedByTokenId()).isEqualTo("token-2");
        assertThatThrownBy(() -> token.consume(NOW.plusSeconds(2), "token-3"))
                .isInstanceOf(IllegalStateException.class);
    }
}
