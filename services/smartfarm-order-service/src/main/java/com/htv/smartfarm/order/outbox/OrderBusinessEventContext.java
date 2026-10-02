package com.htv.smartfarm.order.outbox;

public record OrderBusinessEventContext(
        String actorId,
        String sagaId,
        String stepKey,
        String previousStatus,
        String newStatus,
        String reasonCode,
        String reason
) {
    public static OrderBusinessEventContext transition(String actorId, String sagaId,
            String stepKey, String previousStatus, String newStatus) {
        return new OrderBusinessEventContext(actorId, sagaId, stepKey,
                previousStatus, newStatus, null, null);
    }
}
