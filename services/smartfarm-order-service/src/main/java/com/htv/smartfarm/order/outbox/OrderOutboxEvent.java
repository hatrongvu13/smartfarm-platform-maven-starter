package com.htv.smartfarm.order.outbox;

/**
 * Outbox row joined with its order aggregate. event_id is retained across every publish retry.
 */
public record OrderOutboxEvent(String eventId, String tenantId, String aggregateId, long aggregateVersion,
                               String eventType, String correlationId, long createdAt, String farmId, String batchId,
                               String status, String currencyCode, long totalMinor, String failureReason) {
}
