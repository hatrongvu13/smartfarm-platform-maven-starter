package com.htv.smartfarm.order.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, String> {
    Optional<OrderEntity> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);
    Optional<OrderEntity> findByTenantIdAndId(String tenantId, String id);

    @Query("""
            select o from OrderEntity o
            where o.tenantId = :tenant and o.farmId = :farm
              and (:status is null or o.status = :status)
            order by o.createdAt desc, o.id
            """)
    List<OrderEntity> list(@Param("tenant") String tenant, @Param("farm") String farm,
                           @Param("status") String status, Pageable page);
}
