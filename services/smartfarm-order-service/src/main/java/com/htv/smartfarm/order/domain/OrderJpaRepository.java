package com.htv.smartfarm.order.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, String> {
    Optional<OrderEntity> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);
    Optional<OrderEntity> findByTenantIdAndId(String tenantId, String id);
}
