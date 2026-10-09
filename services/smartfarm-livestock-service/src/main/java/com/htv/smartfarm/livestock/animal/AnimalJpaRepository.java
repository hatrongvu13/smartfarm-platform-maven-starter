package com.htv.smartfarm.livestock.animal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA access to {@link AnimalEntity}.
 */
public interface AnimalJpaRepository extends JpaRepository<AnimalEntity, String> {

    Optional<AnimalEntity> findByTenantIdAndId(String tenantId, String id);

    Optional<AnimalEntity> findByTenantIdAndFarmIdAndTagCode(String tenantId, String farmId, String tagCode);

    @Query("""
            select a from AnimalEntity a
            where a.tenantId = :tenant and a.farmId = :farm
              and (:batch is null or a.batchId = :batch)
            order by a.createdAt desc, a.id
            """)
    List<AnimalEntity> list(@Param("tenant") String tenant, @Param("farm") String farm,
                            @Param("batch") String batch, Pageable page);
}
