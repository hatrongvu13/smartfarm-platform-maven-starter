package com.htv.smartfarm.reporting.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA access to {@link ExportJobEntity}. */
public interface ExportJobJpaRepository extends JpaRepository<ExportJobEntity, String> {

    Optional<ExportJobEntity> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);

    Optional<ExportJobEntity> findByTenantIdAndId(String tenantId, String id);

    @Query("""
            select j from ExportJobEntity j
            where j.tenantId = :tenant and j.farmId = :farm
            order by j.createdAt desc, j.id
            """)
    List<ExportJobEntity> list(@Param("tenant") String tenant, @Param("farm") String farm, Pageable page);

    /** Queued jobs, oldest-first — the export worker's queue. */
    @Query("select j from ExportJobEntity j where j.status = 'EXPORT_STATUS_QUEUED' order by j.createdAt")
    List<ExportJobEntity> queued(Pageable page);
}
