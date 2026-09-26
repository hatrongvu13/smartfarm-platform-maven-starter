package com.htv.smartfarm.health.store;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA access to {@link VaccinationEntity}. */
public interface VaccinationJpaRepository extends JpaRepository<VaccinationEntity, String> {

    Optional<VaccinationEntity> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);

    List<VaccinationEntity> findByTenantIdAndAnimalIdOrderByAdministeredAtDescIdDesc(
            String tenantId, String animalId, Pageable pageable);
}
