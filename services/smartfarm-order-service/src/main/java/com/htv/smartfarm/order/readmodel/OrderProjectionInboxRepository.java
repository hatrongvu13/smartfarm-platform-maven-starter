package com.htv.smartfarm.order.readmodel;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface OrderProjectionInboxRepository
        extends JpaRepository<OrderProjectionInboxEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OrderProjectionInboxEntity e where e.tenantId = :tenantId and e.aggregateId = :aggregateId and e.aggregateVersion = :version and e.status = :status")
    Optional<OrderProjectionInboxEntity> findVersionForUpdate(
            @Param("tenantId") String tenantId, @Param("aggregateId") String aggregateId,
            @Param("version") long version, @Param("status") OrderProjectionInboxStatus status);
    long countByStatus(OrderProjectionInboxStatus status);
    List<OrderProjectionInboxEntity> findTop100ByStatusOrderByReceivedAtAsc(OrderProjectionInboxStatus status);
}
