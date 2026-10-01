package com.htv.smartfarm.order.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderLineJpaRepository extends JpaRepository<OrderLineEntity, String> {
    List<OrderLineEntity> findByOrderIdOrderByLineNo(String orderId);
    List<OrderLineEntity> findByOrderIdInOrderByOrderIdAscLineNoAsc(List<String> orderIds);

    @Modifying
    @Query("delete from OrderLineEntity l where l.orderId = :orderId")
    int deleteByOrderId(@Param("orderId") String orderId);
}
