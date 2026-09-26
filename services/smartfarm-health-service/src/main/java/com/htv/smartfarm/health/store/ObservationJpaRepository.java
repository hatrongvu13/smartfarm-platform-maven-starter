package com.htv.smartfarm.health.store;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA access to {@link ObservationEntity}. */
public interface ObservationJpaRepository extends JpaRepository<ObservationEntity, String> {

    Optional<ObservationEntity> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);

    List<ObservationEntity> findByTenantIdAndAnimalIdOrderByObservedAtDescIdDesc(
            String tenantId, String animalId, Pageable pageable);
}
