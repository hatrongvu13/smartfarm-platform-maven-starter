package com.htv.smartfarm.order.saga.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface OrderSagaJpaRepository extends JpaRepository<OrderSagaEntity, String> {
    Optional<OrderSagaEntity> findByTenantIdAndOrderId(String tenantId, String orderId);
    boolean existsByTenantIdAndOrderId(String tenantId, String orderId);
    long countByStatus(OrderSagaStatus status);
    @Query("select min(s.createdAt) from OrderSagaEntity s where s.status in :statuses")
    Instant oldestActive(@Param("statuses") List<OrderSagaStatus> statuses);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from OrderSagaEntity s where s.status in :statuses and s.claimedAt is null and s.nextAttemptAt <= :now order by s.nextAttemptAt, s.createdAt, s.id")
    List<OrderSagaEntity> lockReady(@Param("statuses") List<OrderSagaStatus> statuses, @Param("now") Instant now, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from OrderSagaEntity s where s.status in :statuses and s.claimedAt < :before order by s.claimedAt, s.id")
    List<OrderSagaEntity> lockStale(@Param("statuses") List<OrderSagaStatus> statuses, @Param("before") Instant before, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from OrderSagaEntity s where s.status = :status and s.manualReviewUntil <= :now order by s.manualReviewUntil, s.id")
    List<OrderSagaEntity> lockExpiredManualReviews(@Param("status") OrderSagaStatus status, @Param("now") Instant now, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from OrderSagaEntity s where s.status in :statuses and s.processingDeadlineAt <= :now order by s.processingDeadlineAt, s.id")
    List<OrderSagaEntity> lockExpiredProcessing(@Param("statuses") List<OrderSagaStatus> statuses, @Param("now") Instant now, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from OrderSagaEntity s where s.id = :sagaId")
    Optional<OrderSagaEntity> findByIdForUpdate(@Param("sagaId") String sagaId);
}
