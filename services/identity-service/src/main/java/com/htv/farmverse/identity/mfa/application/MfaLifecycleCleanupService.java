package com.htv.farmverse.identity.mfa.application;

import java.time.Clock;

import com.htv.farmverse.identity.mfa.domain.AuthenticatorStatus;
import com.htv.farmverse.identity.mfa.domain.MfaChallengeStatus;
import com.htv.farmverse.identity.mfa.repository.MfaChallengeRepository;
import com.htv.farmverse.identity.mfa.repository.UserAuthenticatorRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MfaLifecycleCleanupService {

    private final UserAuthenticatorRepository authenticators;
    private final MfaChallengeRepository challenges;
    private final Clock clock;

    @Scheduled(
            fixedDelayString = "${farmverse.identity.mfa.cleanup-interval:1h}"
    )
    @Transactional
    public void cleanupExpiredPendingState() {
        var now = clock.instant();
        int authenticatorCount = authenticators
                .deleteByStatusAndEnrollmentExpiresAtLessThanEqual(
                        AuthenticatorStatus.PENDING,
                        now
                );
        int challengeCount = challenges
                .deleteByExpiresAtLessThanEqualAndStatus(
                        now,
                        MfaChallengeStatus.PENDING
                );
        if (authenticatorCount > 0 || challengeCount > 0) {
            log.info(
                    "Expired MFA pending state cleaned: authenticators={}, challenges={}, at={}",
                    authenticatorCount,
                    challengeCount,
                    now
            );
        }
    }
}
