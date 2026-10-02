package com.htv.smartfarm.order.outbox.administration;

import java.time.Instant;

public record OrderOutboxInspection(
        String eventId,
        String tenantId,
        String aggregateId,
        long aggregateVersion,
        String eventType,
        String status,
        int attemptCount,
        Instant nextAttemptAt,
        Instant claimedAt,
        String lastErrorCode,
        long createdAt
) { }
