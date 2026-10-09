package com.htv.smartfarm.livestock.schedule;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA access to {@link ScheduleEntity}.
 */
public interface ScheduleJpaRepository extends JpaRepository<ScheduleEntity, String> {

    Optional<ScheduleEntity> findByTenantIdAndId(String tenantId, String id);

    @Query("""
            select s from ScheduleEntity s
            where s.tenantId = :tenant and s.farmId = :farm
            order by s.createdAt desc, s.id
            """)
    List<ScheduleEntity> list(@Param("tenant") String tenant, @Param("farm") String farm, Pageable page);

    /**
     * Enabled schedules whose next run time has arrived — the generator's work queue.
     */
    @Query("""
            select s from ScheduleEntity s
            where s.enabled = true and s.nextRunAt is not null and s.nextRunAt <= :now
            order by s.nextRunAt
            """)
    List<ScheduleEntity> due(@Param("now") long now, Pageable page);
}
