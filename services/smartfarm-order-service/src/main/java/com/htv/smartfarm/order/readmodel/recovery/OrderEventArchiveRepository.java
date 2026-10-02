package com.htv.smartfarm.order.readmodel.recovery;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderEventArchiveRepository extends JpaRepository<OrderEventArchiveEntity, String> {
    Optional<OrderEventArchiveEntity> findByTenantIdAndAggregateIdAndAggregateVersion(
            String tenantId, String aggregateId, long aggregateVersion);
}
