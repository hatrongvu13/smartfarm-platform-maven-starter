package com.htv.smartfarm.identity.messaging.outbox;

import java.time.Instant;

public record IdentityOutboxMessage(
        String eventId,
        String topic,
        byte[] payload,
        int qos,
        int attemptCount,
        Instant occurredAt
) {
    public IdentityOutboxMessage {
        payload = payload.clone();
    }
}
