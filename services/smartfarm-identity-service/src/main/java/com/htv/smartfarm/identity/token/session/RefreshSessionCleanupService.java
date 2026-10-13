package com.htv.smartfarm.identity.token.session;

import java.time.Clock;
import java.time.Duration;
import com.htv.smartfarm.identity.token.session.repository.RefreshSessionJpaRepository;
import com.htv.smartfarm.identity.token.session.repository.RefreshTokenJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshSessionCleanupService {
    private final RefreshTokenJpaRepository tokens;
    private final RefreshSessionJpaRepository sessions;
    private final Clock clock;

    @Value("${smartfarm.identity.refresh-cleanup.retention:P7D}")
    private Duration retention;

    @Scheduled(cron = "${smartfarm.identity.refresh-cleanup.cron:0 17 3 * * *}")
    @Transactional
    public void cleanupExpiredSessions() {
        var before = clock.instant().minus(retention);
        int tokenCount = tokens.deleteExpired(before);
        int sessionCount = sessions.deleteExpired(before);
        if (tokenCount > 0 || sessionCount > 0) {
            log.info(
                    "Expired refresh data cleaned: tokens={}, sessions={}, before={}",
                    tokenCount,
                    sessionCount,
                    before
            );
        }
    }
}
