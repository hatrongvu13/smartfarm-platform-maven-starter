package com.htv.smartfarm.inventory.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA access to {@link MovementEntity}. */
public interface MovementJpaRepository extends JpaRepository<MovementEntity, String> {

    Optional<MovementEntity> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);
}
