package com.htv.smartfarm.order.readmodel.recovery;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface OrderProjectionGapRepository extends JpaRepository<OrderProjectionGapEntity, String> {
    Optional<OrderProjectionGapEntity> findByTenantIdAndAggregateId(String tenantId, String aggregateId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from OrderProjectionGapEntity g where g.tenantId = :tenantId and g.aggregateId = :aggregateId")
    Optional<OrderProjectionGapEntity> findAggregateForUpdate(
            @Param("tenantId") String tenantId, @Param("aggregateId") String aggregateId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from OrderProjectionGapEntity g where g.status in :statuses and g.nextRecoveryAt <= :now order by g.firstDetectedAt, g.id")
    List<OrderProjectionGapEntity> lockReady(@Param("statuses") List<OrderProjectionGapStatus> statuses,
            @Param("now") Instant now, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from OrderProjectionGapEntity g where g.status = :status and g.claimedAt < :before order by g.claimedAt, g.id")
    List<OrderProjectionGapEntity> lockStale(@Param("status") OrderProjectionGapStatus status,
            @Param("before") Instant before, Pageable pageable);
    long countByStatus(OrderProjectionGapStatus status);
}
