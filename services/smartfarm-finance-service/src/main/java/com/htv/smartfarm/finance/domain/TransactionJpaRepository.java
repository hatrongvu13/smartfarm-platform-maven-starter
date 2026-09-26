package com.htv.smartfarm.finance.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionJpaRepository extends JpaRepository<TransactionEntity, String> {
    Optional<TransactionEntity> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);
    Optional<TransactionEntity> findByTenantIdAndId(String tenantId, String id);
}
