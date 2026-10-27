package com.htv.farmverse.identity.mfa;

import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.htv.farmverse.identity.mfa.application.MfaLifecycleCleanupService;
import com.htv.farmverse.identity.mfa.domain.AuthenticatorStatus;
import com.htv.farmverse.identity.mfa.domain.MfaChallengeStatus;
import com.htv.farmverse.identity.mfa.repository.MfaChallengeRepository;
import com.htv.farmverse.identity.mfa.repository.UserAuthenticatorRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MfaLifecycleCleanupServiceTest {

    @Mock
    private UserAuthenticatorRepository authenticators;

    @Mock
    private MfaChallengeRepository challenges;

    @Test
    void removesPendingStateExpiredAtCurrentInstant() {
        Instant now = Instant.parse("2026-10-16T00:00:00Z");
        var cleanup = new MfaLifecycleCleanupService(
                authenticators,
                challenges,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        cleanup.cleanupExpiredPendingState();

        verify(authenticators).deleteByStatusAndEnrollmentExpiresAtLessThanEqual(
                AuthenticatorStatus.PENDING,
                now
        );
        verify(challenges).deleteByExpiresAtLessThanEqualAndStatus(
                now,
                MfaChallengeStatus.PENDING
        );
    }
}
