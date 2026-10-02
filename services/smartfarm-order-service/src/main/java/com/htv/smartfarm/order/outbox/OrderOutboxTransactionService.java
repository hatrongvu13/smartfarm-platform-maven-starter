package com.htv.smartfarm.order.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderOutboxTransactionService {
    private final OrderOutboxJpaRepository repository;
    private final OrderOutboxProperties properties;
    private final Clock clock;
    public OrderOutboxTransactionService(OrderOutboxJpaRepository repository,
            OrderOutboxProperties properties, Clock clock) {
        this.repository = repository; this.properties = properties; this.clock = clock;
    }
    @Transactional
    public List<OrderOutboxEvent> claimBatch() {
        Instant now = clock.instant();
        var rows = repository.lockReady(List.of(OrderOutboxStatus.NEW, OrderOutboxStatus.FAILED),
                now, PageRequest.of(0, properties.batchSize()));
        rows.forEach(value -> value.claim(now));
        return rows.stream().map(this::event).toList();
    }
    @Transactional
    public void markPublished(String eventId) {
        repository.findById(eventId).filter(value -> value.getStatus() == OrderOutboxStatus.PUBLISHING)
                .ifPresent(value -> value.published(clock.instant()));
    }
    @Transactional
    public void markFailed(String eventId, String errorCode) {
        repository.findById(eventId).filter(value -> value.getStatus() == OrderOutboxStatus.PUBLISHING)
                .ifPresent(value -> {
                    int attempt = value.getAttemptCount() + 1;
                    value.failed(clock.instant().plus(retryDelay(attempt)), errorCode, properties.maximumAttempts());
                });
    }

    /** Dead-letter a poison (permanent-data) row immediately so it cannot block the batch. */
    @Transactional
    public void markDead(String eventId, String errorCode) {
        repository.findById(eventId).filter(value -> value.getStatus() == OrderOutboxStatus.PUBLISHING)
                .ifPresent(value -> value.dead(errorCode));
    }
    @Transactional
    public int recoverStaleClaims() {
        Instant now = clock.instant();
        var rows = repository.lockStale(OrderOutboxStatus.PUBLISHING,
                now.minus(properties.publishingTimeout()), PageRequest.of(0, properties.batchSize()));
        rows.forEach(value -> value.recover(now));
        return rows.size();
    }
    @Transactional
    public int cleanupPublished() {
        return repository.deletePublishedBefore(OrderOutboxStatus.PUBLISHED,
                clock.instant().minus(properties.publishedRetention()));
    }
    private OrderOutboxEvent event(OrderOutboxEntity value) {
        return new OrderOutboxEvent(value.getEventId(), value.getTenantId(), value.getAggregateId(),
                value.getAggregateVersion(), value.getEventType(), value.getCorrelationId(),
                value.getCreatedAt(), value.getFarmId(),
                value.getBatchId(), value.getOrderStatus(), value.getCurrencyCode(), value.getTotalMinor(),
                value.getFailureReason(), value.getActorId(), value.getSagaId(), value.getStepKey(),
                value.getPreviousStatus(), value.getNewStatus(), value.getReasonCode(),
                value.getEventReason());
    }
    private Duration retryDelay(int attempt) {
        long initial = properties.initialRetry().toMillis();
        long maximum = properties.maximumRetry().toMillis();
        long calculated = initial * (1L << Math.min(16, Math.max(0, attempt - 1)));
        return Duration.ofMillis(Math.min(maximum, calculated));
    }
}
