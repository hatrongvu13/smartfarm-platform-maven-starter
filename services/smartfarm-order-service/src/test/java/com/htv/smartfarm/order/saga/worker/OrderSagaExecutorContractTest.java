package com.htv.smartfarm.order.saga.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class OrderSagaExecutorContractTest {
    @Test
    void remoteExecutorsMustNotOpenDatabaseTransactions() {
        assertThat(OrderSagaForwardStepExecutor.class.isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(OrderSagaCompensationStepExecutor.class.isAnnotationPresent(Transactional.class)).isFalse();
        for (Method method : OrderSagaForwardStepExecutor.class.getDeclaredMethods()) {
            assertThat(method.isAnnotationPresent(Transactional.class)).isFalse();
        }
        for (Method method : OrderSagaCompensationStepExecutor.class.getDeclaredMethods()) {
            assertThat(method.isAnnotationPresent(Transactional.class)).isFalse();
        }
    }
}
