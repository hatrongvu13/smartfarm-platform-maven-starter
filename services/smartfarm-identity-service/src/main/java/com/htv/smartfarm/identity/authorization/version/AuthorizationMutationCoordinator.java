package com.htv.smartfarm.identity.authorization.version;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Coordinates version increment and outbox invalidation in the caller transaction. */
@Service
@RequiredArgsConstructor
public class AuthorizationMutationCoordinator {
    private final AuthorizationVersionService versions;
    private final AuthorizationInvalidationRecorder invalidations;

    public AuthorizationVersionTuple subjectChanged(
            String tenantId, String subjectId, String actorId,
            String correlationId, String reasonCode
    ) {
        AuthorizationVersionTuple value = versions.incrementSubject(tenantId, subjectId);
        invalidations.recordSubjectInvalidation(
                tenantId, subjectId, actorId, correlationId, value, reasonCode);
        return value;
    }

    public long tenantChanged(
            String tenantId, String actorId, String correlationId, String reasonCode
    ) {
        long value = versions.incrementTenant(tenantId);
        invalidations.recordTenantInvalidation(
                tenantId, actorId, correlationId, value, reasonCode);
        return value;
    }
}
