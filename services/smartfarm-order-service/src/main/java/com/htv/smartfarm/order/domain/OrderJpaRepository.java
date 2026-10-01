package com.htv.smartfarm.order.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, String> {
    Optional<OrderEntity> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);
    Optional<OrderEntity> findByTenantIdAndId(String tenantId, String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.tenantId = :tenantId and o.id = :id")
    Optional<OrderEntity> findByTenantIdAndIdForUpdate(
            @Param("tenantId") String tenantId,
            @Param("id") String id
    );

    @Query("""
            select o from OrderEntity o
            where o.tenantId = :tenant and o.farmId = :farm
              and (:status is null or o.status = :status)
            order by o.createdAt desc, o.id
            """)
    List<OrderEntity> list(@Param("tenant") String tenant, @Param("farm") String farm,
                           @Param("status") String status, Pageable page);

    @Query("""
            select o from OrderEntity o
            where o.tenantId = :tenantId
              and (:farmId is null or o.farmId = :farmId)
              and (:status is null or o.status = :status)
              and (:createdFrom is null or o.createdAt >= :createdFrom)
              and (:createdTo is null or o.createdAt <= :createdTo)
              and (:cursorCreatedAt is null
                   or o.createdAt < :cursorCreatedAt
                   or (o.createdAt = :cursorCreatedAt and o.id > :cursorId))
              and (:warehouseId is null or exists (
                    select l.id from OrderLineEntity l
                    where l.orderId = o.id and l.warehouseId = :warehouseId
              ))
            order by o.createdAt desc, o.id asc
            """)
    List<OrderEntity> listCursor(
            @Param("tenantId") String tenantId,
            @Param("farmId") String farmId,
            @Param("status") String status,
            @Param("warehouseId") String warehouseId,
            @Param("createdFrom") Long createdFrom,
            @Param("createdTo") Long createdTo,
            @Param("cursorCreatedAt") Long cursorCreatedAt,
            @Param("cursorId") String cursorId,
            Pageable pageable
    );
}
