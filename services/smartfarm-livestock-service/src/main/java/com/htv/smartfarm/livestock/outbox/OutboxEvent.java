package com.htv.smartfarm.livestock.outbox;

/**
 * Outbox row joined with its task. Retain event_id across every publish retry.
 */
public record OutboxEvent(String eventId, String tenantId, String aggregateId, String eventType,
                          String correlationId, long createdAt, String farmId, String title, String assigneeId,
                          String taskStatus) {
}
