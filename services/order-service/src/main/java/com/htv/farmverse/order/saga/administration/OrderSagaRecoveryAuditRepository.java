package com.htv.farmverse.order.saga.administration;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderSagaRecoveryAuditRepository
        extends JpaRepository<OrderSagaRecoveryAuditEntity, String> {
}
