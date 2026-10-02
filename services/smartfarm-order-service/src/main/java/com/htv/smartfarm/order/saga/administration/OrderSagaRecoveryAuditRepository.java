package com.htv.smartfarm.order.saga.administration;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderSagaRecoveryAuditRepository
        extends JpaRepository<OrderSagaRecoveryAuditEntity, String> {
}
