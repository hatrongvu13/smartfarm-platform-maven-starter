package com.htv.smartfarm.identity.messaging.command;

public record IdentityMqttDispatchMessage(
        String commandId,
        String tenantId,
        String actorId,
        String correlationId,
        String commandType,
        byte[] payload,
        int attemptCount
) {
    public IdentityMqttDispatchMessage {
        payload = payload.clone();
    }
}
