package com.htv.smartfarm.finance.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DebtJpaRepository extends JpaRepository<DebtEntity, String> {
    Optional<DebtEntity> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);
    Optional<DebtEntity> findByTenantIdAndId(String tenantId, String id);
}
