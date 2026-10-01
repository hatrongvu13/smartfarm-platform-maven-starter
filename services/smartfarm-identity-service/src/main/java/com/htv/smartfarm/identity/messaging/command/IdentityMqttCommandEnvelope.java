package com.htv.smartfarm.identity.messaging.command;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

public record IdentityMqttCommandEnvelope(
        String commandId,
        String commandType,
        int schemaVersion,
        String tenantId,
        String actorId,
        String correlationId,
        Instant issuedAt,
        JsonNode payload
) {
}
