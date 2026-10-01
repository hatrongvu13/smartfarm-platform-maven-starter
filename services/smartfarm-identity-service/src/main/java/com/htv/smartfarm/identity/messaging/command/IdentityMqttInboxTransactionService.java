package com.htv.smartfarm.identity.messaging.command;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityMqttInboxTransactionService {

    private final IdentityMqttInboxRepository inbox;
    private final IdentityMqttCommandProperties properties;
    private final Clock clock;

    public IdentityMqttInboxTransactionService(
            IdentityMqttInboxRepository inbox,
            IdentityMqttCommandProperties properties,
            Clock clock
    ) {
        this.inbox = inbox;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public List<IdentityMqttDispatchMessage> claimBatch() {
        Instant now = clock.instant();
        var rows = inbox.lockDispatchable(
                List.of(IdentityMqttCommandStatus.RECEIVED, IdentityMqttCommandStatus.FAILED),
                now, PageRequest.of(0, properties.dispatchBatchSize()));
        rows.removeIf(row -> row.getAttemptCount() >= properties.maximumAttempts());
        rows.forEach(row -> row.claim(now));
        return rows.stream().map(row -> new IdentityMqttDispatchMessage(
                row.getCommandId(), row.getTenantId(), row.getActorId(),
                row.getCorrelationId(), row.getCommandType(), row.getPayload(),
                row.getAttemptCount())).toList();
    }

    @Transactional
    public void markProcessed(String commandId) {
        inbox.findById(commandId).ifPresent(row -> row.processed(clock.instant()));
    }

    @Transactional
    public void markFailed(String commandId, String errorCode) {
        inbox.findById(commandId).ifPresent(row -> row.failed(
                clock.instant().plus(retryDelay(row.getAttemptCount() + 1)),
                errorCode, properties.maximumAttempts()));
    }

    @Transactional
    public int recoverStale() {
        Instant now = clock.instant();
        var rows = inbox.lockStale(IdentityMqttCommandStatus.PROCESSING,
                now.minus(properties.processingTimeout()),
                PageRequest.of(0, properties.dispatchBatchSize()));
        rows.forEach(row -> row.recover(now));
        return rows.size();
    }

    private Duration retryDelay(int attempt) {
        long value = properties.initialRetry().toMillis()
                * (1L << Math.min(16, Math.max(0, attempt - 1)));
        return Duration.ofMillis(Math.min(properties.maximumRetry().toMillis(), value));
    }
}
