package com.htv.smartfarm.order.saga.persistence;

public record OrderSagaClaim(
        String sagaId,
        String tenantId,
        String orderId,
        String actorId,
        String correlationId,
        OrderSagaStatus status,
        String currentStepKey,
        int attemptCount
) {
}
