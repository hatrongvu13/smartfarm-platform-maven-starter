package com.htv.smartfarm.identity.authorization.version;

import java.util.LinkedHashMap;
import java.util.Map;
import com.htv.smartfarm.identity.messaging.event.IdentityIntegrationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationInvalidationRecorder {
    private final IdentityIntegrationEventPublisher events;

    public AuthorizationInvalidationRecorder(IdentityIntegrationEventPublisher events) {
        this.events = events;
    }

    /**
     * Must be called inside the same transaction as the authorization mutation and
     * version increment. IdentityIntegrationEventPublisher records into sf_identity_outbox.
     */
    public String recordSubjectInvalidation(
            String tenantId,
            String subjectId,
            String actorId,
            String correlationId,
            AuthorizationVersionTuple versions,
            String reasonCode
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("subjectId", required(subjectId, "subjectId"));
        data.put("tenantVersion", versions.tenantVersion());
        data.put("subjectVersion", versions.subjectVersion());
        data.put("reasonCode", required(reasonCode, "reasonCode"));
        return events.publish(
                required(tenantId, "tenantId"),
                actorId,
                correlationId,
                null,
                "identity.authorization.invalidated",
                "authorization-subject",
                subjectId,
                versions.subjectVersion(),
                data
        );
    }

    public String recordTenantInvalidation(
            String tenantId,
            String actorId,
            String correlationId,
            long tenantVersion,
            String reasonCode
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tenantVersion", tenantVersion);
        data.put("reasonCode", required(reasonCode, "reasonCode"));
        return events.publish(
                required(tenantId, "tenantId"),
                actorId,
                correlationId,
                null,
                "identity.authorization.invalidated",
                "authorization-tenant",
                tenantId,
                tenantVersion,
                data
        );
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
