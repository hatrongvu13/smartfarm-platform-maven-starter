package com.htv.smartfarm.order.saga.persistence;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderSagaInsertService {
    private final OrderSagaJpaRepository sagas;
    private final OrderSagaStepJpaRepository steps;
    public OrderSagaInsertService(OrderSagaJpaRepository sagas, OrderSagaStepJpaRepository steps) {
        this.sagas = sagas; this.steps = steps;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(OrderSagaEntity saga, List<OrderSagaStepEntity> graph) {
        sagas.saveAndFlush(saga);
        steps.saveAllAndFlush(graph);
    }
}
