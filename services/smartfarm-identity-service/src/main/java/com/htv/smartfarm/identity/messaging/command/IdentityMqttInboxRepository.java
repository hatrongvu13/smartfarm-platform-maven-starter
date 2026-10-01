package com.htv.smartfarm.identity.messaging.command;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdentityMqttInboxRepository
        extends JpaRepository<IdentityMqttInboxEntity, String> {

    long countByStatus(IdentityMqttCommandStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e from IdentityMqttInboxEntity e
            where e.status in :statuses and e.nextAttemptAt <= :now
            order by e.receivedAt, e.commandId
            """)
    List<IdentityMqttInboxEntity> lockDispatchable(
            @Param("statuses") List<IdentityMqttCommandStatus> statuses,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e from IdentityMqttInboxEntity e
            where e.status = :status and e.claimedAt < :before
            order by e.claimedAt
            """)
    List<IdentityMqttInboxEntity> lockStale(
            @Param("status") IdentityMqttCommandStatus status,
            @Param("before") Instant before,
            Pageable pageable
    );

    @Modifying
    @Query("delete from IdentityMqttInboxEntity e where e.receivedAt < :before")
    int deleteReceivedBefore(@Param("before") Instant before);
}
