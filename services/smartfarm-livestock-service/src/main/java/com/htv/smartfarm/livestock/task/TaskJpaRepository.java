package com.htv.smartfarm.livestock.task;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA access to {@link TaskEntity}. */
public interface TaskJpaRepository extends JpaRepository<TaskEntity, String> {

    Optional<TaskEntity> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);

    Optional<TaskEntity> findByTenantIdAndId(String tenantId, String id);

    /** List by farm, ordered newest-first. Optional status/assignee filters (null = no filter). */
    @Query("""
            select t from TaskEntity t
            where t.tenantId = :tenant and t.farmId = :farm
              and (:status is null or t.status = :status)
              and (:assignee is null or t.assigneeId = :assignee)
            order by t.createdAt desc, t.id
            """)
    List<TaskEntity> list(@Param("tenant") String tenant, @Param("farm") String farm,
                          @Param("status") String status, @Param("assignee") String assignee, Pageable page);

    /** Assigned tasks past their accept deadline, not yet accepted, not yet notified. */
    @Query("""
            select t from TaskEntity t
            where t.status = 'TASK_STATUS_ASSIGNED'
              and t.acceptDeadlineAt is not null and t.acceptDeadlineAt < :now
              and t.acceptedAt is null and t.acceptOverdueNotifiedAt is null
            order by t.acceptDeadlineAt
            """)
    List<TaskEntity> acceptOverdue(@Param("now") long now, Pageable page);

    /** Accepted tasks past their report deadline, not yet reported/completed, not yet notified. */
    @Query("""
            select t from TaskEntity t
            where t.status = 'TASK_STATUS_ACCEPTED'
              and t.reportDueAt is not null and t.reportDueAt < :now
              and t.reportedAt is null and t.reportOverdueNotifiedAt is null
            order by t.reportDueAt
            """)
    List<TaskEntity> reportOverdue(@Param("now") long now, Pageable page);
}
