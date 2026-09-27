package com.htv.smartfarm.order.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderLineJpaRepository extends JpaRepository<OrderLineEntity, String> {
    List<OrderLineEntity> findByOrderIdOrderByLineNo(String orderId);
}
