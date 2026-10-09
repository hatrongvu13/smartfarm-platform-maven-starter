package com.htv.smartfarm.identity.authorization.version;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubjectAuthorizationVersionRepository
        extends JpaRepository<SubjectAuthorizationVersionEntity, String> {

    Optional<SubjectAuthorizationVersionEntity> findByTenantIdAndSubjectId(
            String tenantId,
            String subjectId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select v from SubjectAuthorizationVersionEntity v
            where v.tenantId = :tenantId and v.subjectId = :subjectId
            """)
    Optional<SubjectAuthorizationVersionEntity> findForUpdate(
            @Param("tenantId") String tenantId,
            @Param("subjectId") String subjectId
    );
}
