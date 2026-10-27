package com.htv.farmverse.identity.authorization.version;

import com.htv.farmverse.identity.messaging.event.IdentityIntegrationEventPublisher;

import java.util.Objects;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

/**
 * Records authorization invalidation events in the identity outbox.
 *
 * <p>Each method must be called within the same transaction as the
 * corresponding authorization mutation and version increment. The
 * {@link IdentityIntegrationEventPublisher} persists the event in
 * {@code sf_identity_outbox}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizationInvalidationRecorder {

    private static final String TENANT_INVALIDATION_SUBJECT_ID = "";

    private static final long TENANT_INVALIDATION_SUBJECT_VERSION = 0L;

    private final IdentityIntegrationEventPublisher events;

    /**
     * Records an invalidation event for a subject authorization change.
     *
     * @param tenantId      tenant whose authorization data changed
     * @param subjectId     subject whose authorization data changed
     * @param actorId       actor that performed the mutation
     * @param correlationId request correlation identifier
     * @param versions      current tenant and subject authorization versions
     * @param reasonCode    code describing the reason for invalidation
     * @return identifier of the recorded integration event
     */
    public String recordSubjectInvalidation(
            String tenantId,
            String subjectId,
            String actorId,
            String correlationId,
            AuthorizationVersionTuple versions,
            String reasonCode
    ) {
        String normalizedTenantId = required(
                tenantId,
                "tenantId"
        );

        String normalizedSubjectId = required(
                subjectId,
                "subjectId"
        );

        String normalizedReasonCode = required(
                reasonCode,
                "reasonCode"
        );

        AuthorizationVersionTuple requiredVersions =
                Objects.requireNonNull(
                        versions,
                        "versions must not be null"
                );

        log.debug(
                "Recording subject authorization invalidation: "
                        + "tenantId={}, subjectId={}, actorId={}, "
                        + "tenantVersion={}, subjectVersion={}, "
                        + "reasonCode={}, correlationId={}",
                normalizedTenantId,
                normalizedSubjectId,
                actorId,
                requiredVersions.tenantVersion(),
                requiredVersions.subjectVersion(),
                normalizedReasonCode,
                correlationId
        );

        String eventId = events.publishAuthorizationInvalidated(
                normalizedTenantId,
                normalizedSubjectId,
                actorId,
                correlationId,
                requiredVersions.tenantVersion(),
                requiredVersions.subjectVersion(),
                normalizedReasonCode
        );

        log.debug(
                "Subject authorization invalidation recorded: "
                        + "eventId={}, tenantId={}, subjectId={}, "
                        + "tenantVersion={}, subjectVersion={}, "
                        + "reasonCode={}, correlationId={}",
                eventId,
                normalizedTenantId,
                normalizedSubjectId,
                requiredVersions.tenantVersion(),
                requiredVersions.subjectVersion(),
                normalizedReasonCode,
                correlationId
        );

        return eventId;
    }

    /**
     * Records an invalidation event for a tenant-wide authorization change.
     *
     * <p>A tenant-wide event uses an empty subject identifier and subject
     * version {@code 0}, indicating that consumers must invalidate
     * authorization data for the entire tenant.</p>
     *
     * @param tenantId      tenant whose authorization data changed
     * @param actorId       actor that performed the mutation
     * @param correlationId request correlation identifier
     * @param tenantVersion current tenant authorization version
     * @param reasonCode    code describing the reason for invalidation
     * @return identifier of the recorded integration event
     */
    public String recordTenantInvalidation(
            String tenantId,
            String actorId,
            String correlationId,
            long tenantVersion,
            String reasonCode
    ) {
        String normalizedTenantId = required(
                tenantId,
                "tenantId"
        );

        String normalizedReasonCode = required(
                reasonCode,
                "reasonCode"
        );

        log.debug(
                "Recording tenant authorization invalidation: "
                        + "tenantId={}, actorId={}, tenantVersion={}, "
                        + "reasonCode={}, correlationId={}",
                normalizedTenantId,
                actorId,
                tenantVersion,
                normalizedReasonCode,
                correlationId
        );

        String eventId = events.publishAuthorizationInvalidated(
                normalizedTenantId,
                TENANT_INVALIDATION_SUBJECT_ID,
                actorId,
                correlationId,
                tenantVersion,
                TENANT_INVALIDATION_SUBJECT_VERSION,
                normalizedReasonCode
        );

        log.debug(
                "Tenant authorization invalidation recorded: "
                        + "eventId={}, tenantId={}, tenantVersion={}, "
                        + "reasonCode={}, correlationId={}",
                eventId,
                normalizedTenantId,
                tenantVersion,
                normalizedReasonCode,
                correlationId
        );

        return eventId;
    }

    private static String required(
            String value,
            String field
    ) {
        if (value == null || value.isBlank()) {
            log.warn(
                    "Authorization invalidation validation failed: "
                            + "required field is blank, field={}",
                    field
            );

            throw new IllegalArgumentException(
                    field + " must not be blank"
            );
        }

        return value.trim();
    }
}