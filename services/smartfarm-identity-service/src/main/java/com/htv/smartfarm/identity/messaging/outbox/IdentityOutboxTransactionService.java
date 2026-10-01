package com.htv.smartfarm.identity.messaging.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityOutboxTransactionService {

    private final IdentityOutboxRepository repository;
    private final IdentityOutboxProperties properties;
    private final Clock clock;

    public IdentityOutboxTransactionService(
            IdentityOutboxRepository repository,
            IdentityOutboxProperties properties,
            Clock clock
    ) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public List<IdentityOutboxMessage> claimBatch(int qos) {
        Instant now = clock.instant();
        List<IdentityOutboxEntity> values = repository.lockReady(
                List.of(IdentityOutboxStatus.NEW, IdentityOutboxStatus.FAILED),
                now,
                PageRequest.of(0, properties.batchSize())
        );
        values.forEach(value -> value.claim(now));
        return values.stream()
                .map(value -> new IdentityOutboxMessage(
                        value.getEventId(),
                        value.getTopic(),
                        value.getPayload(),
                        qos,
                        value.getAttemptCount(),
                        value.getOccurredAt()
                ))
                .toList();
    }

    @Transactional
    public void markPublished(String eventId) {
        repository.findById(eventId).ifPresent(value -> value.published(clock.instant()));
    }

    @Transactional
    public void markFailed(String eventId, String errorCode) {
        repository.findById(eventId).ifPresent(value -> value.failed(
                clock.instant().plus(retryDelay(value.getAttemptCount() + 1)),
                errorCode,
                properties.maximumAttempts()
        ));
    }

    @Transactional
    public int recoverStaleClaims() {
        Instant now = clock.instant();
        List<IdentityOutboxEntity> stale = repository.lockStale(
                IdentityOutboxStatus.PUBLISHING,
                now.minus(properties.publishingTimeout()),
                PageRequest.of(0, properties.batchSize())
        );
        stale.forEach(value -> value.recover(now));
        return stale.size();
    }

    @Transactional
    public boolean retryDead(String eventId) {
        return repository.findById(eventId)
                .filter(value -> value.getStatus() == IdentityOutboxStatus.DEAD)
                .map(value -> {
                    value.retryDead(clock.instant());
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public int cleanupPublished() {
        return repository.deletePublishedBefore(
                IdentityOutboxStatus.PUBLISHED,
                clock.instant().minus(properties.publishedRetention())
        );
    }

    private Duration retryDelay(int attempt) {
        long initial = properties.initialRetry().toMillis();
        long maximum = properties.maximumRetry().toMillis();
        long value = initial * (1L << Math.min(16, Math.max(0, attempt - 1)));
        return Duration.ofMillis(Math.min(maximum, value));
    }
}
