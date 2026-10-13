package com.htv.smartfarm.identity.authorization.version;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

/**
 * Coordinates authorization version increments and outbox invalidation
 * records within the caller's transaction.
 *
 * <p>This coordinator intentionally does not define its own transaction
 * boundary. Version updates and invalidation records therefore participate
 * in the transaction opened by the calling application service.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizationMutationCoordinator {

    private final AuthorizationVersionService versions;
    private final AuthorizationInvalidationRecorder invalidations;

    public AuthorizationVersionTuple subjectChanged(
            String tenantId,
            String subjectId,
            String actorId,
            String correlationId,
            String reasonCode
    ) {
        log.debug(
                "Processing subject authorization change: "
                        + "tenantId={}, subjectId={}, actorId={}, "
                        + "reasonCode={}, correlationId={}",
                tenantId,
                subjectId,
                actorId,
                reasonCode,
                correlationId
        );

        AuthorizationVersionTuple version =
                versions.incrementSubject(
                        tenantId,
                        subjectId
                );

        invalidations.recordSubjectInvalidation(
                tenantId,
                subjectId,
                actorId,
                correlationId,
                version,
                reasonCode
        );

        log.debug(
                "Subject authorization change recorded successfully: "
                        + "tenantId={}, subjectId={}, actorId={}, "
                        + "tenantVersion={}, subjectVersion={}, "
                        + "reasonCode={}, correlationId={}",
                tenantId,
                subjectId,
                actorId,
                version.tenantVersion(),
                version.subjectVersion(),
                reasonCode,
                correlationId
        );

        return version;
    }

    public long tenantChanged(
            String tenantId,
            String actorId,
            String correlationId,
            String reasonCode
    ) {
        log.debug(
                "Processing tenant authorization change: "
                        + "tenantId={}, actorId={}, reasonCode={}, "
                        + "correlationId={}",
                tenantId,
                actorId,
                reasonCode,
                correlationId
        );

        long tenantVersion =
                versions.incrementTenant(tenantId);

        invalidations.recordTenantInvalidation(
                tenantId,
                actorId,
                correlationId,
                tenantVersion,
                reasonCode
        );

        log.debug(
                "Tenant authorization change recorded successfully: "
                        + "tenantId={}, actorId={}, tenantVersion={}, "
                        + "reasonCode={}, correlationId={}",
                tenantId,
                actorId,
                tenantVersion,
                reasonCode,
                correlationId
        );

        return tenantVersion;
    }
}