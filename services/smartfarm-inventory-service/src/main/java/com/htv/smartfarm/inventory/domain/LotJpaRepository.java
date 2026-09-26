package com.htv.smartfarm.inventory.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA access to {@link LotEntity}. */
public interface LotJpaRepository extends JpaRepository<LotEntity, String> {

    Optional<LotEntity> findByTenantIdAndId(String tenantId, String id);
}
