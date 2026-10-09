
package com.htv.smartfarm.inventory.domain.movement;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovementJpaRepository extends JpaRepository<MovementEntity, String> {
    Optional<MovementEntity> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);
    List<MovementEntity> findByTenantIdAndLotIdOrderByIdAsc(String tenantId, String lotId, Pageable page);
}
