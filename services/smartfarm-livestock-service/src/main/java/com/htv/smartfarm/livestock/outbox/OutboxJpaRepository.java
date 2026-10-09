package com.htv.smartfarm.livestock.outbox;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.htv.smartfarm.livestock.task.TaskEntity;

/**
 * Spring Data JPA access to {@link OutboxEntity}.
 */
public interface OutboxJpaRepository extends JpaRepository<OutboxEntity, String> {

    /**
     * Pending outbox rows joined with their task aggregate, ordered oldest-first.
     * Mirrors the original SQL join {@code sf_outbox o join sf_task t on t.id=o.aggregate_id
     * and t.tenant_id=o.tenant_id where o.status='NEW'}. The join is expressed on
     * matching columns because there is no declared FK association between the
     * two aggregates.
     */
    @Query("""
            select new com.htv.smartfarm.livestock.outbox.OutboxEvent(
                o.eventId, o.tenantId, o.aggregateId, o.eventType, o.correlationId, o.createdAt,
                t.farmId, t.title, t.assigneeId, t.status)
            from OutboxEntity o, TaskEntity t
            where t.id = o.aggregateId and t.tenantId = o.tenantId and o.status = 'NEW'
            order by o.createdAt, o.eventId
            """)
    List<OutboxEvent> findPending(Pageable pageable);
}
