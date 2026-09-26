package com.htv.smartfarm.inventory.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA access to {@link ItemEntity}. */
public interface ItemJpaRepository extends JpaRepository<ItemEntity, String> {

    Optional<ItemEntity> findByTenantIdAndId(String tenantId, String id);

    Optional<ItemEntity> findByTenantIdAndSku(String tenantId, String sku);
}
