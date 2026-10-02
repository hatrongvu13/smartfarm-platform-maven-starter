package com.htv.smartfarm.order.outbox;

public record OrderOutboxEvent(
        String eventId,
        String tenantId,
        String aggregateId,
        long aggregateVersion,
        String eventType,
        String correlationId,
        long createdAt,
        String farmId,
        String batchId,
        String status,
        String currencyCode,
        long totalMinor,
        String failureReason,
        String actorId,
        String sagaId,
        String stepKey,
        String previousStatus,
        String newStatus,
        String reasonCode,
        String reason
) { }
