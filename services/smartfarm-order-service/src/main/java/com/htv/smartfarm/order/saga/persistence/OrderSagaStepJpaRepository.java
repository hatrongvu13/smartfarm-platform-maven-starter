package com.htv.smartfarm.order.saga.persistence;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface OrderSagaStepJpaRepository extends JpaRepository<OrderSagaStepEntity, String> {
    List<OrderSagaStepEntity> findBySagaIdOrderBySequenceNoAsc(String sagaId);
    Optional<OrderSagaStepEntity> findBySagaIdAndStepKey(String sagaId, String stepKey);
    boolean existsBySagaIdAndStepKey(String sagaId, String stepKey);
    long countBySagaIdAndStatus(String sagaId, OrderSagaStepStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from OrderSagaStepEntity s where s.sagaId = :sagaId order by s.sequenceNo")
    List<OrderSagaStepEntity> findBySagaIdForUpdate(@Param("sagaId") String sagaId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from OrderSagaStepEntity s where s.sagaId = :sagaId and s.stepKey = :stepKey")
    Optional<OrderSagaStepEntity> findBySagaIdAndStepKeyForUpdate(@Param("sagaId") String sagaId, @Param("stepKey") String stepKey);
}
