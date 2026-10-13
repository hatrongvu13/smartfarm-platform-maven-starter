package com.htv.smartfarm.identity.token.session;

import java.time.Clock;
import com.htv.smartfarm.identity.token.session.repository.RefreshSessionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Commits replay revocation even when the outer rotation request fails. */
@Service
@RequiredArgsConstructor
public class RefreshReplayRevocationService {
    private final RefreshSessionJpaRepository sessions;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revoke(String sessionId, String reason) {
        sessions.findById(sessionId).ifPresent(session -> {
            var now = clock.instant();
            session.revoke(now, reason);
        });
    }
}
