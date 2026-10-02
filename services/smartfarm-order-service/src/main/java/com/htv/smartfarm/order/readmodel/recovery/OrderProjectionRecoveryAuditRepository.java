package com.htv.smartfarm.order.readmodel.recovery;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderProjectionRecoveryAuditRepository
        extends JpaRepository<OrderProjectionRecoveryAuditEntity, String> {
}
