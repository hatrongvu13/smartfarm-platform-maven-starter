package com.htv.smartfarm.order.outbox;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Pending outbox rows joined with their order aggregate, oldest-first, so the relay can build a
 * full {@code OrderChanged} snapshot (status, total, failure reason) from the current order row.
 */
public interface OrderOutboxJpaRepository extends JpaRepository<OrderOutboxEntity, String> {

    @Query("""
            select new com.htv.smartfarm.order.outbox.OrderOutboxEvent(
                o.eventId, o.tenantId, o.aggregateId, o.eventType, o.correlationId, o.createdAt,
                r.farmId, r.batchId, r.status, r.currencyCode, r.totalMinor, r.failureReason)
            from OrderOutboxEntity o, com.htv.smartfarm.order.domain.OrderEntity r
            where r.id = o.aggregateId and r.tenantId = o.tenantId and o.status = 'NEW'
            order by o.createdAt, o.eventId
            """)
    List<OrderOutboxEvent> findPending(Pageable pageable);
}
