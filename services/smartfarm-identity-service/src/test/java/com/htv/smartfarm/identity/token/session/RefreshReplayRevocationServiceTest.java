package com.htv.smartfarm.identity.token.session;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import com.htv.smartfarm.identity.token.session.entity.RefreshSessionEntity;
import com.htv.smartfarm.identity.token.session.repository.RefreshSessionJpaRepository;
import com.htv.smartfarm.identity.token.session.repository.RefreshTokenJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class RefreshReplayRevocationServiceTest {
    @Test
    void revokeMarksSessionAndAllTokens() {
        var sessions = org.mockito.Mockito.mock(RefreshSessionJpaRepository.class);
        Instant now = Instant.parse("2026-10-13T08:00:00Z");
        var session = new RefreshSessionEntity(
                "s1", "f1", "u1", "t1", "gateway", now.minusSeconds(1), now.plusSeconds(60)
        );
        when(sessions.findById("s1")).thenReturn(Optional.of(session));
        var service = new RefreshReplayRevocationService(
                sessions, Clock.fixed(now, ZoneOffset.UTC)
        );

        service.revoke("s1", "REFRESH_TOKEN_REUSE");

        verify(sessions).findById("s1");
        org.assertj.core.api.Assertions.assertThat(session.getRevokeReason())
                .isEqualTo("REFRESH_TOKEN_REUSE");
    }

    @Test
    void serviceUsesRequiresNewPropagation() throws Exception {
        Transactional annotation = RefreshReplayRevocationService.class
                .getMethod("revoke", String.class, String.class)
                .getAnnotation(Transactional.class);
        org.assertj.core.api.Assertions.assertThat(annotation.propagation())
                .isEqualTo(Propagation.REQUIRES_NEW);
    }
}
