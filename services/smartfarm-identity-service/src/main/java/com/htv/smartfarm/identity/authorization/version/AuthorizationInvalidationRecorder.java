package com.htv.smartfarm.identity.authorization.version;

import com.htv.smartfarm.identity.messaging.event.IdentityIntegrationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthorizationInvalidationRecorder {
    private final IdentityIntegrationEventPublisher events;

    /**
     * Must be called inside the same transaction as the authorization mutation and
     * version increment. IdentityIntegrationEventPublisher records into sf_identity_outbox.
     */
    public String recordSubjectInvalidation(
            String tenantId, String subjectId, String actorId,
            String correlationId, AuthorizationVersionTuple versions,
            String reasonCode) {
        return events.publishAuthorizationInvalidated(required(tenantId,"tenantId"),
                required(subjectId,"subjectId"), actorId, correlationId,
                versions.tenantVersion(), versions.subjectVersion(), required(reasonCode,"reasonCode"));
    }

    public String recordTenantInvalidation(
            String tenantId, String actorId, String correlationId,
            long tenantVersion, String reasonCode) {
        return events.publishAuthorizationInvalidated(required(tenantId,"tenantId"),
                "", actorId, correlationId, tenantVersion, 0L, required(reasonCode,"reasonCode"));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
