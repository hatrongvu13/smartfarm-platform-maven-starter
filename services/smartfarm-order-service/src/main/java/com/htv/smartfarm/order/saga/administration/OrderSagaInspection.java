package com.htv.smartfarm.order.saga.administration;

import java.time.Instant;
import java.util.List;

public record OrderSagaInspection(
        String sagaId,
        String tenantId,
        String orderId,
        String status,
        String terminalIntent,
        String currentStepKey,
        int attemptCount,
        Instant nextAttemptAt,
        Instant claimedAt,
        Instant compensationDeadlineAt,
        String lastErrorCode,
        String lastErrorMessage,
        List<Step> steps
) {
    public record Step(
            String stepKey,
            int sequenceNo,
            String stepType,
            String status,
            String orderLineId,
            String externalReferenceId,
            int attemptCount,
            Instant nextAttemptAt,
            Instant claimedAt,
            String lastErrorCode,
            String lastErrorMessage
    ) { }
}
