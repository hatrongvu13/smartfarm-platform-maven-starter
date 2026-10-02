package com.htv.smartfarm.order.readmodel;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderViewJpaRepository extends JpaRepository<OrderView, String> {

    Optional<OrderView> findByTenantIdAndOrderId(String tenantId, String orderId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from OrderView v where v.tenantId = :tenantId and v.orderId = :orderId")
    Optional<OrderView> findByTenantIdAndOrderIdForUpdate(
            @Param("tenantId") String tenantId, @Param("orderId") String orderId);

    @Query("""
            select v from OrderView v
            where v.tenantId = :tenant and v.farmId = :farm
              and (:status is null or v.status = :status)
            order by v.lastEventAt desc, v.orderId
            """)
    List<OrderView> list(@Param("tenant") String tenant, @Param("farm") String farm,
                         @Param("status") String status, Pageable page);
}
