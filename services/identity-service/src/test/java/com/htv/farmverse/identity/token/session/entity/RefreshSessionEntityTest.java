package com.htv.farmverse.identity.token.session.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RefreshSessionEntityTest {
    private static final Instant NOW = Instant.parse("2026-10-13T08:00:00Z");

    @Test
    void activeSessionCanBeTouchedAndRevokedIdempotently() {
        var session = new RefreshSessionEntity(
                "session-1", "family-1", "user-1", "tenant-1",
                "gateway", NOW, NOW.plusSeconds(3600)
        );
        assertThat(session.active(NOW)).isTrue();
        session.touch(NOW.plusSeconds(1));
        assertThat(session.getLastUsedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(session.revoke(NOW.plusSeconds(2), "LOGOUT")).isTrue();
        assertThat(session.revoke(NOW.plusSeconds(3), "SECOND")).isFalse();
        assertThat(session.getRevokeReason()).isEqualTo("LOGOUT");
    }

    @Test
    void expiredSessionCannotBeTouched() {
        var session = new RefreshSessionEntity(
                "session-1", "family-1", "user-1", "tenant-1",
                "gateway", NOW.minusSeconds(7200), NOW.minusSeconds(1)
        );
        assertThatThrownBy(() -> session.touch(NOW))
                .isInstanceOf(IllegalStateException.class);
    }
}
