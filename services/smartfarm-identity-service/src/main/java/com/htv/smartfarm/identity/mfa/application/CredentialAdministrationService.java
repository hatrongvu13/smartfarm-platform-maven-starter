package com.htv.smartfarm.identity.mfa.application;

import com.htv.smartfarm.identity.mfa.repository.RecoveryCodeRepository;
import com.htv.smartfarm.identity.mfa.repository.UserAuthenticatorRepository;
import com.htv.smartfarm.identity.token.RefreshRepository;
import com.htv.smartfarm.identity.messaging.event.IdentityIntegrationEventPublisher;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CredentialAdministrationService {

    private final UserAuthenticatorRepository authenticators;
    private final RecoveryCodeRepository recoveryCodes;
    private final RefreshRepository refresh;
    private final IdentityIntegrationEventPublisher events;

    public CredentialAdministrationService(UserAuthenticatorRepository authenticators,
            RecoveryCodeRepository recoveryCodes, RefreshRepository refresh,
            IdentityIntegrationEventPublisher events) {
        this.authenticators = authenticators;
        this.recoveryCodes = recoveryCodes;
        this.refresh = refresh;
        this.events = events;
    }

    @Transactional
    public void resetUserMfa(String userId) {
        resetUserMfa(null, userId, userId, null);
    }

    @Transactional
    public void resetUserMfa(
            String tenantId,
            String userId,
            String actorId,
            String correlationId) {
        var values = authenticators.findAllByUserIdOrderByCreatedAtAsc(userId);
        values.forEach(value -> {
            value.disable();
            recoveryCodes.deleteByAuthenticatorId(value.getId());
        });
        refresh.revokeUser(userId);
        if (tenantId != null && !tenantId.isBlank()) {
            events.publish(
                    tenantId,
                    actorId,
                    correlationId,
                    null,
                    "identity.mfa.reset",
                    "user-security",
                    userId,
                    0,
                    java.util.Map.of(
                            "subjectId", userId,
                            "disabledAuthenticatorCount", values.size()
                    )
            );
        }
    }
}
