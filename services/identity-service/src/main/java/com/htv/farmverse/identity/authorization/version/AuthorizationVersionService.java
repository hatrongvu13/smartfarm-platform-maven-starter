package com.htv.farmverse.identity.authorization.version;

import com.htv.farmverse.common.exception.NotFoundException;
import com.htv.farmverse.identity.tenant.repository.TenantMembershipRepository;
import com.htv.farmverse.identity.tenant.repository.TenantRepository;

import java.time.Clock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizationVersionService {

    private final TenantAuthorizationVersionRepository tenants;
    private final SubjectAuthorizationVersionRepository subjects;
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AuthorizationVersionTuple current(
            String tenantId,
            String subjectId
    ) {
        requireText(tenantId, "tenantId");
        requireText(subjectId, "subjectId");

        log.debug(
                "Reading current authorization versions: "
                        + "tenantId={}, subjectId={}",
                tenantId,
                subjectId
        );

        long tenantVersion = tenants.findById(tenantId)
                .map(TenantAuthorizationVersionEntity::getVersion)
                .orElse(0L);

        long subjectVersion = subjects
                .findByTenantIdAndSubjectId(tenantId, subjectId)
                .map(SubjectAuthorizationVersionEntity::getVersion)
                .orElse(0L);

        log.debug(
                "Current authorization versions resolved: "
                        + "tenantId={}, subjectId={}, tenantVersion={}, "
                        + "subjectVersion={}",
                tenantId,
                subjectId,
                tenantVersion,
                subjectVersion
        );

        return new AuthorizationVersionTuple(
                tenantVersion,
                subjectVersion
        );
    }

    @Transactional
    public AuthorizationVersionTuple incrementSubject(
            String tenantId,
            String subjectId
    ) {
        requireText(tenantId, "tenantId");
        requireText(subjectId, "subjectId");

        log.debug(
                "Incrementing subject authorization version: "
                        + "tenantId={}, subjectId={}",
                tenantId,
                subjectId
        );

        SubjectAuthorizationVersionEntity subjectAuthorizationVersion =
                subjects.findForUpdate(tenantId, subjectId)
                        .orElseGet(
                                () -> createSubjectVersion(
                                        tenantId,
                                        subjectId
                                )
                        );

        long subjectVersion =
                subjectAuthorizationVersion.increment(clock.instant());

        long currentTenantVersion = tenantVersion(tenantId);

        log.info(
                "Subject authorization version incremented: "
                        + "tenantId={}, subjectId={}, tenantVersion={}, "
                        + "subjectVersion={}",
                tenantId,
                subjectId,
                currentTenantVersion,
                subjectVersion
        );

        return new AuthorizationVersionTuple(
                currentTenantVersion,
                subjectVersion
        );
    }

    @Transactional
    public long incrementTenant(String tenantId) {
        requireText(tenantId, "tenantId");

        log.debug(
                "Incrementing tenant authorization version: tenantId={}",
                tenantId
        );

        TenantAuthorizationVersionEntity tenantAuthorizationVersion =
                tenants.findForUpdate(tenantId)
                        .orElseGet(
                                () -> createTenantVersion(tenantId)
                        );

        long incrementedVersion =
                tenantAuthorizationVersion.increment(clock.instant());

        log.info(
                "Tenant authorization version incremented: "
                        + "tenantId={}, tenantVersion={}",
                tenantId,
                incrementedVersion
        );

        return incrementedVersion;
    }

    private long tenantVersion(String tenantId) {
        long currentVersion = tenants.findById(tenantId)
                .map(TenantAuthorizationVersionEntity::getVersion)
                .orElse(0L);

        log.debug(
                "Tenant authorization version resolved: "
                        + "tenantId={}, tenantVersion={}",
                tenantId,
                currentVersion
        );

        return currentVersion;
    }

    private TenantAuthorizationVersionEntity createTenantVersion(
            String tenantId
    ) {
        log.debug(
                "Creating tenant authorization version: tenantId={}",
                tenantId
        );

        if (!tenantRepository.existsById(tenantId)) {
            log.warn(
                    "Cannot create tenant authorization version: "
                            + "tenant does not exist, tenantId={}",
                    tenantId
            );

            throw NotFoundException.entity(
                    "Tenant",
                    tenantId
            );
        }

        try {
            TenantAuthorizationVersionEntity createdVersion =
                    tenants.saveAndFlush(
                            new TenantAuthorizationVersionEntity(
                                    tenantId,
                                    clock.instant()
                            )
                    );

            log.info(
                    "Tenant authorization version created: "
                            + "tenantId={}, tenantVersion={}",
                    tenantId,
                    createdVersion.getVersion()
            );

            return createdVersion;
        } catch (DataIntegrityViolationException raceCondition) {
            log.debug(
                    "Concurrent tenant authorization version creation "
                            + "detected; loading existing row: tenantId={}",
                    tenantId
            );

            return tenants.findForUpdate(tenantId)
                    .orElseThrow(() -> {
                        log.warn(
                                "Tenant authorization version could not be "
                                        + "loaded after concurrent creation: "
                                        + "tenantId={}",
                                tenantId
                        );

                        return raceCondition;
                    });
        }
    }

    private SubjectAuthorizationVersionEntity createSubjectVersion(
            String tenantId,
            String subjectId
    ) {
        log.debug(
                "Creating subject authorization version: "
                        + "tenantId={}, subjectId={}",
                tenantId,
                subjectId
        );

        var membership = membershipRepository
                .findByTenantAndUserForUpdate(
                        tenantId,
                        subjectId
                )
                .orElseThrow(() -> {
                    log.warn(
                            "Cannot create subject authorization version: "
                                    + "tenant membership does not exist, "
                                    + "tenantId={}, subjectId={}",
                            tenantId,
                            subjectId
                    );

                    return NotFoundException.entity(
                            "TenantMembership",
                            tenantId + ":" + subjectId
                    );
                });

        try {
            SubjectAuthorizationVersionEntity createdVersion =
                    subjects.saveAndFlush(
                            new SubjectAuthorizationVersionEntity(
                                    membership.getId(),
                                    tenantId,
                                    subjectId,
                                    clock.instant()
                            )
                    );

            log.info(
                    "Subject authorization version created: "
                            + "tenantId={}, subjectId={}, membershipId={}, "
                            + "subjectVersion={}",
                    tenantId,
                    subjectId,
                    membership.getId(),
                    createdVersion.getVersion()
            );

            return createdVersion;
        } catch (DataIntegrityViolationException raceCondition) {
            log.debug(
                    "Concurrent subject authorization version creation "
                            + "detected; loading existing row: "
                            + "tenantId={}, subjectId={}",
                    tenantId,
                    subjectId
            );

            return subjects.findForUpdate(tenantId, subjectId)
                    .orElseThrow(() -> {
                        log.warn(
                                "Subject authorization version could not be "
                                        + "loaded after concurrent creation: "
                                        + "tenantId={}, subjectId={}",
                                tenantId,
                                subjectId
                        );

                        return raceCondition;
                    });
        }
    }

    private static void requireText(
            String value,
            String field
    ) {
        if (value == null || value.isBlank()) {
            log.warn(
                    "Authorization version validation failed: "
                            + "required field is blank, field={}",
                    field
            );

            throw new IllegalArgumentException(
                    field + " must not be blank"
            );
        }
    }
}