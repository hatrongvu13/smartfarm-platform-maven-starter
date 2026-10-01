package com.htv.smartfarm.identity.mfa.application;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import com.htv.smartfarm.common.exception.NotFoundException;
import com.htv.smartfarm.identity.mfa.application.model.MfaChallenge;
import com.htv.smartfarm.identity.mfa.config.MfaProperties;
import com.htv.smartfarm.identity.mfa.crypto.DigestSupport;
import com.htv.smartfarm.identity.mfa.crypto.SecretCipher;
import com.htv.smartfarm.identity.mfa.domain.AuthenticatorStatus;
import com.htv.smartfarm.identity.mfa.domain.AuthenticatorType;
import com.htv.smartfarm.identity.mfa.domain.MfaChallengeEntity;
import com.htv.smartfarm.identity.mfa.domain.MfaChallengePurpose;
import com.htv.smartfarm.identity.mfa.repository.MfaChallengeRepository;
import com.htv.smartfarm.identity.mfa.repository.RecoveryCodeRepository;
import com.htv.smartfarm.identity.mfa.repository.UserAuthenticatorRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MfaChallengeService {

    private final MfaChallengeRepository challenges;
    private final UserAuthenticatorRepository authenticators;
    private final RecoveryCodeRepository recoveryCodes;
    private final SecretCipher cipher;
    private final TotpService totp;
    private final MfaProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public MfaChallengeService(
            MfaChallengeRepository challenges,
            UserAuthenticatorRepository authenticators,
            RecoveryCodeRepository recoveryCodes,
            SecretCipher cipher,
            TotpService totp,
            MfaProperties properties,
            Clock clock
    ) {
        this.challenges = challenges;
        this.authenticators = authenticators;
        this.recoveryCodes = recoveryCodes;
        this.cipher = cipher;
        this.totp = totp;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public MfaChallenge create(
            String userId,
            String tenantId,
            MfaChallengePurpose purpose
    ) {
        properties.validateRuntime();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
        Instant expiresAt = clock.instant()
                .plus(properties.getChallengeTtl());

        challenges.save(new MfaChallengeEntity(
                UUID.randomUUID().toString(),
                userId,
                tenantId,
                purpose,
                DigestSupport.sha256(raw),
                expiresAt
        ));

        return new MfaChallenge(raw, expiresAt);
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void verifyTotp(String challengeToken, String code) {
        MfaChallengeEntity challenge = challengeForUpdate(challengeToken);
        UserAuthenticatorSelection selection = activeTotp(challenge.getUserId());
        Instant now = clock.instant();
        long step = totp.verify(
                cipher.decrypt(selection.authenticator().getSecretCiphertext()),
                code,
                now
        );

        if (step < 0) {
            challenge.recordFailure(properties.getMaximumAttempts(), now);
            throw new IllegalArgumentException("TOTP code is invalid");
        }

        selection.authenticator().acceptTotpStep(step, now);
        challenge.verify(now);
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void verifyRecoveryCode(String challengeToken, String rawCode) {
        MfaChallengeEntity challenge = challengeForUpdate(challengeToken);
        UserAuthenticatorSelection selection = activeTotp(challenge.getUserId());
        var recoveryCode = recoveryCodes.findUnusedForUpdate(
                selection.authenticator().getId(),
                DigestSupport.sha256(rawCode)
        ).orElse(null);
        Instant now = clock.instant();

        if (recoveryCode == null) {
            challenge.recordFailure(properties.getMaximumAttempts(), now);
            throw new IllegalArgumentException("Recovery code is invalid");
        }

        recoveryCode.consume(now);
        challenge.verify(now);
    }

    @Transactional
    public MfaChallengeEntity consume(
            String challengeToken,
            MfaChallengePurpose expectedPurpose
    ) {
        MfaChallengeEntity challenge = challengeForUpdate(challengeToken);
        if (challenge.getPurpose() != expectedPurpose) {
            throw new IllegalArgumentException("Challenge purpose is invalid");
        }
        challenge.consume(clock.instant());
        return challenge;
    }

    @Transactional
    public MfaChallengeEntity requirePending(
            String challengeToken,
            MfaChallengePurpose expectedPurpose
    ) {
        MfaChallengeEntity challenge = challengeForUpdate(challengeToken);
        Instant now = clock.instant();
        if (challenge.getPurpose() != expectedPurpose) {
            throw new IllegalArgumentException("Challenge purpose is invalid");
        }
        if (challenge.expiredAt(now)) {
            challenge.expire();
            throw new IllegalArgumentException("Challenge has expired");
        }
        if (challenge.getStatus()
                != com.htv.smartfarm.identity.mfa.domain.MfaChallengeStatus.PENDING) {
            throw new IllegalArgumentException("Challenge is not pending");
        }
        return challenge;
    }

    @Transactional
    public MfaChallengeEntity completeEnrollment(String challengeToken) {
        MfaChallengeEntity challenge = requirePending(
                challengeToken,
                MfaChallengePurpose.TOTP_ENROLLMENT
        );
        Instant now = clock.instant();
        challenge.verify(now);
        challenge.consume(now);
        return challenge;
    }

    @Transactional(readOnly = true)
    public List<String> availableMethods(String userId) {
        var active = authenticators.findFirstByUserIdAndTypeAndStatus(
                userId,
                AuthenticatorType.TOTP,
                AuthenticatorStatus.ACTIVE
        );
        if (active.isEmpty()) {
            return List.of();
        }
        if (recoveryCodes.existsByAuthenticatorIdAndUsedAtIsNull(
                active.get().getId()
        )) {
            return List.of("TOTP", "RECOVERY_CODE");
        }
        return List.of("TOTP");
    }

    private UserAuthenticatorSelection activeTotp(String userId) {
        var values = authenticators.findActiveForUpdate(
                userId,
                AuthenticatorType.TOTP,
                AuthenticatorStatus.ACTIVE
        );
        if (values.isEmpty()) {
            throw NotFoundException.entity(
                    "ActiveTotpAuthenticator",
                    userId
            );
        }
        return new UserAuthenticatorSelection(values.get(0));
    }

    private MfaChallengeEntity challengeForUpdate(String raw) {
        return challenges.findByChallengeHashForUpdate(
                DigestSupport.sha256(raw)
        ).orElseThrow(() -> NotFoundException.entity(
                "MfaChallenge",
                "unknown"
        ));
    }

    private record UserAuthenticatorSelection(
            com.htv.smartfarm.identity.mfa.domain.UserAuthenticatorEntity authenticator
    ) {
    }
}
