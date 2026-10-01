package com.htv.smartfarm.identity.messaging.command;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class IdentityMqttCommandIntake {

    public enum Result { RECEIVED, REJECTED, DUPLICATE }

    private static final Logger log = LoggerFactory.getLogger(IdentityMqttCommandIntake.class);
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9._:-]{1,100}");

    private final IdentityMqttInboxRepository inbox;
    private final IdentityMqttCommandProperties properties;
    private final IdentityMqttInboxWriter writer;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IdentityMqttCommandIntake(
            IdentityMqttInboxRepository inbox,
            IdentityMqttCommandProperties properties,
            IdentityMqttInboxWriter writer,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.inbox = inbox;
        this.properties = properties;
        this.writer = writer;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public Result accept(String topic, byte[] bytes) {
        Instant now = clock.instant();
        byte[] payload = bytes == null ? new byte[0] : bytes.clone();
        if (payload.length == 0 || payload.length > properties.maximumPayloadBytes()) {
            return reject(topic, payload, null, "PAYLOAD_SIZE_INVALID", now);
        }

        IdentityMqttCommandTopic parsedTopic;
        try {
            parsedTopic = IdentityMqttCommandTopic.parse(topic);
        } catch (IllegalArgumentException exception) {
            return reject(topic, payload, null, exception.getMessage(), now);
        }

        IdentityMqttCommandEnvelope envelope;
        try {
            envelope = objectMapper.readValue(payload, IdentityMqttCommandEnvelope.class);
        } catch (Exception exception) {
            return reject(topic, payload, null, "ENVELOPE_INVALID", now);
        }

        String validation = validate(parsedTopic, envelope, now);
        if (validation != null) {
            return reject(topic, payload, envelope.commandId(), validation, now);
        }

        if (inbox.existsById(envelope.commandId())) return Result.DUPLICATE;
        try {
            writer.saveReceived(envelope, topic, payload, now);
            return Result.RECEIVED;
        } catch (DataIntegrityViolationException duplicate) {
            return Result.DUPLICATE;
        }
    }

    private String validate(
            IdentityMqttCommandTopic topic,
            IdentityMqttCommandEnvelope envelope,
            Instant now
    ) {
        if (envelope == null) return "ENVELOPE_REQUIRED";
        if (!identifier(envelope.commandId())) return "COMMAND_ID_INVALID";
        if (!identifier(envelope.actorId())) return "ACTOR_ID_INVALID";
        if (!identifier(envelope.correlationId())) return "CORRELATION_ID_INVALID";
        if (envelope.commandType() == null || envelope.commandType().isBlank()) return "COMMAND_TYPE_REQUIRED";
        if (!properties.allowedTypes().contains(envelope.commandType())) return "COMMAND_TYPE_NOT_ALLOWED";
        if (!topic.tenantId().equals(envelope.tenantId())) return "TENANT_TOPIC_MISMATCH";
        if (!topic.matchesType(envelope.commandType())) return "COMMAND_TOPIC_MISMATCH";
        if (envelope.schemaVersion() != topic.version() || envelope.schemaVersion() != 1) {
            return "SCHEMA_VERSION_UNSUPPORTED";
        }
        if (envelope.issuedAt() == null) return "ISSUED_AT_REQUIRED";
        if (envelope.issuedAt().isAfter(now.plusSeconds(300))) return "ISSUED_AT_IN_FUTURE";
        if (envelope.payload() == null || envelope.payload().isNull()) return "COMMAND_PAYLOAD_REQUIRED";
        return null;
    }

    private Result reject(
            String topic,
            byte[] payload,
            String suppliedCommandId,
            String code,
            Instant now
    ) {
        String id = identifier(suppliedCommandId)
                ? suppliedCommandId
                : "rejected:" + UUID.randomUUID();
        if (inbox.existsById(id)) return Result.DUPLICATE;
        byte[] retained = payload.length <= properties.maximumPayloadBytes() ? payload : null;
        try {
            writer.saveRejected(
                    id,
                    topic == null || topic.isBlank() ? "unknown" : topic,
                    retained,
                    code,
                    now
            );
        } catch (DataIntegrityViolationException duplicate) {
            return Result.DUPLICATE;
        }
        log.warn("Identity MQTT command rejected: commandId={}, code={}", id, code);
        return Result.REJECTED;
    }

    private boolean identifier(String value) {
        return value != null && IDENTIFIER.matcher(value).matches();
    }
}
