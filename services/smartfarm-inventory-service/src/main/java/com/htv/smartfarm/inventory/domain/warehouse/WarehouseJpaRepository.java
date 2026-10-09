package com.htv.smartfarm.inventory.domain.warehouse;

import java.util.*;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface WarehouseJpaRepository extends JpaRepository<WarehouseEntity, String> {
    Optional<WarehouseEntity> findByTenantIdAndId(String tenant, String id);

    boolean existsByTenantIdAndFarmIdAndCodeIgnoreCase(String tenant, String farm, String code);

    @Query("select w from WarehouseEntity w where w.tenantId=:tenant and (:farm='' or w.farmId=:farm) and (:status is null or w.status=:status) and (:q='' or lower(w.code) like lower(concat('%',:q,'%')) or lower(w.name) like lower(concat('%',:q,'%'))) order by w.code,w.id")
    Page<WarehouseEntity> search(@Param("tenant") String tenant, @Param("farm") String farm, @Param("status") WarehouseStatus status, @Param("q") String q, Pageable page);
}
