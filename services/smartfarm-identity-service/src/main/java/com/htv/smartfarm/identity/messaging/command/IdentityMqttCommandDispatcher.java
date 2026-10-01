package com.htv.smartfarm.identity.messaging.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.htv.smartfarm.identity.administration.application.command.AssignRoleCommand;
import com.htv.smartfarm.identity.administration.application.command.RevokeRoleCommand;
import com.htv.smartfarm.identity.authorization.application.RoleManagementService;
import com.htv.smartfarm.identity.messaging.event.IdentityIntegrationEventPublisher;
import com.htv.smartfarm.identity.tenant.application.TenantMembershipService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "smartfarm.identity.mqtt.commands",
        name = "enabled", havingValue = "true")
public class IdentityMqttCommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(IdentityMqttCommandDispatcher.class);
    private final IdentityMqttInboxTransactionService transactions;
    private final TenantMembershipService memberships;
    private final RoleManagementService roles;
    private final IdentityIntegrationEventPublisher events;
    private final ObjectMapper objectMapper;

    public IdentityMqttCommandDispatcher(
            IdentityMqttInboxTransactionService transactions,
            TenantMembershipService memberships,
            RoleManagementService roles,
            IdentityIntegrationEventPublisher events,
            ObjectMapper objectMapper
    ) {
        this.transactions = transactions;
        this.memberships = memberships;
        this.roles = roles;
        this.events = events;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${smartfarm.identity.mqtt.commands.dispatch-interval:1s}")
    public void dispatch() {
        transactions.recoverStale();
        for (IdentityMqttDispatchMessage message : transactions.claimBatch()) {
            try {
                execute(message);
                transactions.markProcessed(message.commandId());
                publishResult(message, "PROCESSED", null);
            } catch (IllegalArgumentException exception) {
                transactions.markFailed(message.commandId(), "COMMAND_PAYLOAD_INVALID");
                publishResult(message, "FAILED", "COMMAND_PAYLOAD_INVALID");
            } catch (RuntimeException exception) {
                transactions.markFailed(message.commandId(), exception.getClass().getSimpleName());
                log.warn("Identity MQTT command deferred: commandId={}, error={}",
                        message.commandId(), exception.getClass().getSimpleName());
                break;
            }
        }
    }

    private void execute(IdentityMqttDispatchMessage message) {
        JsonNode envelope = read(message.payload());
        JsonNode payload = requiredObject(envelope.get("payload"));
        String subjectId = requiredText(payload, "subjectId");
        switch (message.commandType()) {
            case "identity.membership.enable" -> memberships.activateMembership(
                    message.tenantId(), subjectId, message.actorId(),
                    message.correlationId(), nullableText(payload, "reason"));
            case "identity.membership.suspend" -> memberships.suspendMembership(
                    message.tenantId(), subjectId, message.actorId(),
                    message.correlationId(), nullableText(payload, "reason"));
            case "identity.membership.disable" -> memberships.disableMembership(
                    message.tenantId(), subjectId, message.actorId(),
                    message.correlationId(), nullableText(payload, "reason"));
            case "identity.role.assign" -> roles.assignRole(
                    message.tenantId(), subjectId, requiredText(payload, "roleCode"), message.actorId());
            case "identity.role.revoke" -> roles.revokeRole(
                    message.tenantId(), subjectId, requiredText(payload, "roleCode"));
            default -> throw new IllegalArgumentException("Unsupported command type");
        }
    }

    private void publishResult(IdentityMqttDispatchMessage message, String status, String errorCode) {
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("commandId", message.commandId());
        data.put("commandType", message.commandType());
        data.put("status", status);
        if (errorCode != null) data.put("errorCode", errorCode);
        events.publish(message.tenantId(), message.actorId(), message.correlationId(),
                message.commandId(), "identity.command.result", "mqtt-command",
                message.commandId(), 0, data);
    }

    private JsonNode read(byte[] payload) {
        try { return objectMapper.readTree(payload); }
        catch (Exception exception) { throw new IllegalArgumentException("Invalid command JSON", exception); }
    }

    private JsonNode requiredObject(JsonNode value) {
        if (value == null || !value.isObject()) throw new IllegalArgumentException("payload must be an object");
        return value;
    }

    private String requiredText(JsonNode value, String field) {
        JsonNode node = value.get(field);
        if (node == null || !node.isTextual() || node.asText().isBlank())
            throw new IllegalArgumentException(field + " must not be blank");
        return node.asText().trim();
    }

    private String nullableText(JsonNode value, String field) {
        JsonNode node = value.get(field);
        return node == null || node.isNull() || node.asText().isBlank() ? null : node.asText().trim();
    }
}
