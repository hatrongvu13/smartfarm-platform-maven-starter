package com.htv.smartfarm.order.saga.worker;

public record OrderSagaStepResult(String externalReferenceId) {
    public static OrderSagaStepResult withoutReference() {
        return new OrderSagaStepResult(null);
    }
}
