package com.htv.smartfarm.inventory.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationJpaRepository extends JpaRepository<ReservationEntity, String> {
    Optional<ReservationEntity> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);
    Optional<ReservationEntity> findByTenantIdAndId(String tenantId, String id);
}
