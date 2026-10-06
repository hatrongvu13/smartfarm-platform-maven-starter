package com.htv.smartfarm.identity.mfa.application;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.htv.smartfarm.common.exception.NotFoundException;
import com.htv.smartfarm.identity.account.repository.UserAccountRepository;
import com.htv.smartfarm.identity.mfa.application.model.SecurityProfile;
import com.htv.smartfarm.identity.mfa.application.model.TotpEnrollment;
import com.htv.smartfarm.identity.mfa.application.model.TotpEnrollmentConfirmation;
import com.htv.smartfarm.identity.mfa.config.MfaProperties;
import com.htv.smartfarm.identity.mfa.crypto.Base32Support;
import com.htv.smartfarm.identity.mfa.crypto.DigestSupport;
import com.htv.smartfarm.identity.mfa.crypto.SecretCipher;
import com.htv.smartfarm.identity.mfa.domain.AuthenticatorStatus;
import com.htv.smartfarm.identity.mfa.domain.AuthenticatorType;
import com.htv.smartfarm.identity.mfa.domain.RecoveryCodeEntity;
import com.htv.smartfarm.identity.mfa.domain.UserAuthenticatorEntity;
import com.htv.smartfarm.identity.mfa.repository.RecoveryCodeRepository;
import com.htv.smartfarm.identity.mfa.repository.UserAuthenticatorRepository;
import com.htv.smartfarm.identity.token.RefreshRepository;
import com.htv.smartfarm.identity.messaging.event.IdentityIntegrationEventPublisher;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticatorService {

    private final UserAccountRepository accounts;
    private final UserAuthenticatorRepository authenticators;
    private final RecoveryCodeRepository recoveryCodes;
    private final MfaRequirementService requirements;
    private final RefreshRepository refresh;
    private final SecretCipher cipher;
    private final TotpService totp;
    private final MfaProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final IdentityIntegrationEventPublisher events;

    public AuthenticatorService(UserAccountRepository accounts,
            UserAuthenticatorRepository authenticators,
            RecoveryCodeRepository recoveryCodes,
            MfaRequirementService requirements,
            RefreshRepository refresh, SecretCipher cipher,
            TotpService totp, MfaProperties properties, Clock clock,
            IdentityIntegrationEventPublisher events) {
        this.accounts = accounts;
        this.authenticators = authenticators;
        this.recoveryCodes = recoveryCodes;
        this.requirements = requirements;
        this.refresh = refresh;
        this.cipher = cipher;
        this.totp = totp;
        this.properties = properties;
        this.clock = clock;
        this.events = events;
    }

    @Transactional
    public TotpEnrollment beginTotpEnrollment(String userId, String displayName) {
        properties.validateRuntime();
        var account = accounts.findById(userId)
                .orElseThrow(() -> NotFoundException.entity("UserAccount", userId));
        if (authenticators.existsByUserIdAndTypeAndStatus(
                userId, AuthenticatorType.TOTP, AuthenticatorStatus.ACTIVE)) {
            throw new IllegalStateException("An active TOTP authenticator already exists");
        }
        // A prior begin that was never confirmed leaves a PENDING authenticator. Rather than
        // dead-ending the user (previously threw "A pending TOTP enrollment already exists"),
        // supersede it: delete the stale pending row and issue a fresh secret. Only the
        // confirm step promotes PENDING -> ACTIVE, so discarding an unconfirmed one is safe.
        authenticators.findFirstByUserIdAndTypeAndStatus(
                userId, AuthenticatorType.TOTP, AuthenticatorStatus.PENDING)
                .ifPresent(authenticators::delete);
        byte[] secretBytes = new byte[20];
        random.nextBytes(secretBytes);
        String secret = Base32Support.encode(secretBytes);
        String id = UUID.randomUUID().toString();
        String name = displayName == null || displayName.isBlank()
                ? "Authenticator" : displayName.trim();
        authenticators.save(new UserAuthenticatorEntity(
                id, userId, AuthenticatorType.TOTP, name, cipher.encrypt(secret)));
        String label = encode(properties.getIssuer() + ":" + account.getEmail());
        String uri = "otpauth://totp/" + label
                + "?secret=" + secret
                + "&issuer=" + encode(properties.getIssuer())
                + "&algorithm=SHA1&digits=6&period=30";
        return new TotpEnrollment(id, uri, clock.instant().plus(properties.getChallengeTtl()));
    }

    @Transactional
    public TotpEnrollmentConfirmation confirmTotpEnrollment(
            String userId, String authenticatorId, String code) {
        return confirmTotpEnrollment(null, userId, authenticatorId, code, userId, null);
    }

    @Transactional
    public TotpEnrollmentConfirmation confirmTotpEnrollment(
            String tenantId,
            String userId,
            String authenticatorId,
            String code,
            String actorId,
            String correlationId) {
        properties.validateRuntime();
        UserAuthenticatorEntity authenticator = authenticator(authenticatorId, userId);
        if (authenticator.getStatus() != AuthenticatorStatus.PENDING
                || authenticator.getType() != AuthenticatorType.TOTP) {
            throw new IllegalStateException("TOTP enrollment is not pending");
        }
        Instant now = clock.instant();
        long step = totp.verify(cipher.decrypt(authenticator.getSecretCiphertext()), code, now);
        if (step < 0) throw new IllegalArgumentException("TOTP code is invalid");
        authenticator.activate(now);
        authenticator.acceptTotpStep(step, now);
        List<String> plaintextCodes = generateRecoveryCodes(authenticator.getId());
        publishIfTenant(
                tenantId,
                actorId,
                correlationId,
                "identity.mfa.enrolled",
                userId,
                java.util.Map.of(
                        "subjectId", userId,
                        "authenticatorId", authenticator.getId(),
                        "type", authenticator.getType().name(),
                        "status", authenticator.getStatus().name()
                )
        );
        return new TotpEnrollmentConfirmation(authenticator.getId(), plaintextCodes);
    }

    @Transactional
    public void disableAuthenticator(String tenantId, String userId, String authenticatorId) {
        disableAuthenticator(tenantId, userId, authenticatorId, userId, null);
    }

    @Transactional
    public void disableAuthenticator(
            String tenantId,
            String userId,
            String authenticatorId,
            String actorId,
            String correlationId
    ) {
        UserAuthenticatorEntity authenticator = authenticator(authenticatorId, userId);
        if (authenticator.getType() == AuthenticatorType.TOTP
                && requirements.requiresTotp(tenantId, userId)) {
            throw new IllegalStateException("TOTP is mandatory for the current role");
        }
        authenticator.disable();
        recoveryCodes.deleteByAuthenticatorId(authenticatorId);
        refresh.revokeUser(userId);
        publishIfTenant(
                tenantId,
                actorId,
                correlationId,
                "identity.mfa.disabled",
                userId,
                java.util.Map.of(
                        "subjectId", userId,
                        "authenticatorId", authenticatorId,
                        "type", authenticator.getType().name(),
                        "status", authenticator.getStatus().name()
                )
        );
    }

    @Transactional
    public List<String> regenerateRecoveryCodes(String userId, String authenticatorId) {
        return regenerateRecoveryCodes(null, userId, authenticatorId, userId, null);
    }

    @Transactional
    public List<String> regenerateRecoveryCodes(
            String tenantId,
            String userId,
            String authenticatorId,
            String actorId,
            String correlationId) {
        UserAuthenticatorEntity authenticator = authenticator(authenticatorId, userId);
        if (authenticator.getStatus() != AuthenticatorStatus.ACTIVE) {
            throw new IllegalStateException("Authenticator is not active");
        }
        recoveryCodes.deleteByAuthenticatorId(authenticatorId);
        refresh.revokeUser(userId);
        List<String> values = generateRecoveryCodes(authenticatorId);
        publishIfTenant(
                tenantId,
                actorId,
                correlationId,
                "identity.mfa.recovery-codes-regenerated",
                userId,
                java.util.Map.of(
                        "subjectId", userId,
                        "authenticatorId", authenticatorId,
                        "recoveryCodeCount", values.size()
                )
        );
        return values;
    }

    @Transactional(readOnly = true)
    public SecurityProfile getSecurityProfile(String tenantId, String userId) {
        List<SecurityProfile.AuthenticatorSummary> summaries = authenticators
                .findAllByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(value -> new SecurityProfile.AuthenticatorSummary(
                        value.getId(), value.getType().name(), value.getStatus().name(),
                        value.getDisplayName()))
                .toList();
        boolean enabled = summaries.stream().anyMatch(value ->
                value.type().equals(AuthenticatorType.TOTP.name())
                        && value.status().equals(AuthenticatorStatus.ACTIVE.name()));
        return new SecurityProfile(userId, requirements.requiresTotp(tenantId, userId),
                enabled, summaries);
    }

    private UserAuthenticatorEntity authenticator(String authenticatorId, String userId) {
        UserAuthenticatorEntity result = authenticators.findById(authenticatorId)
                .orElseThrow(() -> NotFoundException.entity("UserAuthenticator", authenticatorId));
        if (!result.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Authenticator does not belong to user");
        }
        return result;
    }

    private List<String> generateRecoveryCodes(String authenticatorId) {
        List<String> plaintext = new ArrayList<>();
        List<RecoveryCodeEntity> entities = new ArrayList<>();
        for (int index = 0; index < properties.getRecoveryCodeCount(); index++) {
            byte[] bytes = new byte[10];
            random.nextBytes(bytes);
            String code = java.util.HexFormat.of().formatHex(bytes).toUpperCase();
            plaintext.add(code);
            entities.add(new RecoveryCodeEntity(UUID.randomUUID().toString(),
                    authenticatorId, DigestSupport.sha256(code)));
        }
        recoveryCodes.saveAll(entities);
        return List.copyOf(plaintext);
    }

    private void publishIfTenant(
            String tenantId,
            String actorId,
            String correlationId,
            String eventType,
            String userId,
            java.util.Map<String, Object> data) {
        if (tenantId == null || tenantId.isBlank()) return;
        events.publish(
                tenantId,
                actorId,
                correlationId,
                null,
                eventType,
                "user-security",
                userId,
                0,
                data
        );
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
