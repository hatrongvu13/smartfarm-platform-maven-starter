package com.htv.smartfarm.order.outbox;

import java.time.Instant;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface OrderOutboxJpaRepository extends JpaRepository<OrderOutboxEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderOutboxEntity o where o.status in :statuses and o.nextAttemptAt <= :now order by o.createdAt, o.eventId")
    List<OrderOutboxEntity> lockReady(@Param("statuses") List<OrderOutboxStatus> statuses,
            @Param("now") Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderOutboxEntity o where o.status = :status and o.claimedAt < :before order by o.claimedAt, o.eventId")
    List<OrderOutboxEntity> lockStale(@Param("status") OrderOutboxStatus status,
            @Param("before") Instant before, Pageable pageable);

    long countByStatus(OrderOutboxStatus status);

    @Modifying
    @Query("delete from OrderOutboxEntity o where o.status = :status and o.publishedAt < :before")
    int deletePublishedBefore(@Param("status") OrderOutboxStatus status, @Param("before") Instant before);
}
