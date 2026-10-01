package com.htv.smartfarm.identity.messaging.outbox;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdentityOutboxRepository extends JpaRepository<IdentityOutboxEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e from IdentityOutboxEntity e
            where e.status in :statuses and e.nextAttemptAt <= :now
            order by e.occurredAt, e.eventId
            """)
    List<IdentityOutboxEntity> lockReady(
            @Param("statuses") List<IdentityOutboxStatus> statuses,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e from IdentityOutboxEntity e
            where e.status = :status and e.claimedAt < :staleBefore
            order by e.claimedAt
            """)
    List<IdentityOutboxEntity> lockStale(
            @Param("status") IdentityOutboxStatus status,
            @Param("staleBefore") Instant staleBefore,
            Pageable pageable
    );

    long countByStatus(IdentityOutboxStatus status);

    @Query("select min(e.occurredAt) from IdentityOutboxEntity e where e.status in :statuses")
    Instant oldestPending(@Param("statuses") List<IdentityOutboxStatus> statuses);

    @Modifying
    @Query("delete from IdentityOutboxEntity e where e.status = :status and e.publishedAt < :before")
    int deletePublishedBefore(
            @Param("status") IdentityOutboxStatus status,
            @Param("before") Instant before
    );
}
