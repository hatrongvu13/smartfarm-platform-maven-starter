package com.htv.farmverse.order.readmodel.recovery;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderProjectionRecoveryAuditRepository
        extends JpaRepository<OrderProjectionRecoveryAuditEntity, String> {
    java.util.List<OrderProjectionRecoveryAuditEntity> findTop100ByTenantIdAndGapIdOrderByOccurredAtDesc(
            String tenantId, String gapId);
}
