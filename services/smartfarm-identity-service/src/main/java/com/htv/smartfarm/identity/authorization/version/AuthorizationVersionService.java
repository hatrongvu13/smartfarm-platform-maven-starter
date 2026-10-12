package com.htv.smartfarm.identity.authorization.version;

import java.time.Clock;
import com.htv.smartfarm.common.exception.NotFoundException;
import com.htv.smartfarm.identity.tenant.repository.TenantMembershipRepository;
import com.htv.smartfarm.identity.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthorizationVersionService {
    private final TenantAuthorizationVersionRepository tenants;
    private final SubjectAuthorizationVersionRepository subjects;
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AuthorizationVersionTuple current(String tenantId, String subjectId) {
        requireText(tenantId, "tenantId");
        requireText(subjectId, "subjectId");
        long tenantVersion = tenants.findById(tenantId)
                .map(TenantAuthorizationVersionEntity::getVersion)
                .orElse(0L);
        long subjectVersion = subjects.findByTenantIdAndSubjectId(tenantId, subjectId)
                .map(SubjectAuthorizationVersionEntity::getVersion)
                .orElse(0L);
        return new AuthorizationVersionTuple(tenantVersion, subjectVersion);
    }

    @Transactional
    public AuthorizationVersionTuple incrementSubject(String tenantId, String subjectId) {
        requireText(tenantId, "tenantId");
        requireText(subjectId, "subjectId");
        SubjectAuthorizationVersionEntity value = subjects.findForUpdate(tenantId, subjectId)
                .orElseGet(() -> createSubjectVersion(tenantId, subjectId));
        long subjectVersion = value.increment(clock.instant());
        long tenantVersion = tenantVersion(tenantId);
        return new AuthorizationVersionTuple(tenantVersion, subjectVersion);
    }

    @Transactional
    public long incrementTenant(String tenantId) {
        requireText(tenantId, "tenantId");
        TenantAuthorizationVersionEntity value = tenants.findForUpdate(tenantId)
                .orElseGet(() -> createTenantVersion(tenantId));
        return value.increment(clock.instant());
    }

    private long tenantVersion(String tenantId) {
        return tenants.findById(tenantId)
                .map(TenantAuthorizationVersionEntity::getVersion)
                .orElse(0L);
    }

    private TenantAuthorizationVersionEntity createTenantVersion(String tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw NotFoundException.entity("Tenant", tenantId);
        }
        try {
            return tenants.saveAndFlush(
                    new TenantAuthorizationVersionEntity(tenantId, clock.instant())
            );
        } catch (DataIntegrityViolationException race) {
            return tenants.findForUpdate(tenantId).orElseThrow(() -> race);
        }
    }

    private SubjectAuthorizationVersionEntity createSubjectVersion(
            String tenantId,
            String subjectId
    ) {
        var membership = membershipRepository.findByTenantAndUserForUpdate(tenantId, subjectId)
                .orElseThrow(() -> NotFoundException.entity(
                        "TenantMembership", tenantId + ":" + subjectId
                ));
        try {
            return subjects.saveAndFlush(new SubjectAuthorizationVersionEntity(
                    membership.getId(), tenantId, subjectId, clock.instant()
            ));
        } catch (DataIntegrityViolationException race) {
            return subjects.findForUpdate(tenantId, subjectId).orElseThrow(() -> race);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
