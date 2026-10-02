package com.htv.smartfarm.order.saga.persistence;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.htv.smartfarm.order.domain.OrderLineJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderSagaTransactionService {
    private final OrderSagaJpaRepository sagas;
    private final OrderSagaStepJpaRepository steps;
    private final OrderLineJpaRepository orderLines;
    private final OrderSagaProperties properties;
    private final OrderSagaInsertService inserts;
    private final Clock clock;

    public OrderSagaTransactionService(OrderSagaJpaRepository sagas, OrderSagaStepJpaRepository steps,
            OrderLineJpaRepository orderLines, OrderSagaProperties properties,
            OrderSagaInsertService inserts, Clock clock) {
        this.sagas = sagas; this.steps = steps; this.orderLines = orderLines;
        this.properties = properties; this.inserts = inserts; this.clock = clock;
    }

    public String create(String tenantId, String orderId, String actorId, String correlationId) {
        var existing = sagas.findByTenantIdAndOrderId(tenantId, orderId).orElse(null);
        if (existing != null) return existing.getId();
        Instant now = clock.instant();
        String sagaId = UUID.randomUUID().toString();
        OrderSagaEntity saga = new OrderSagaEntity(sagaId, tenantId, orderId, actorId, correlationId, now);
        List<OrderSagaStepEntity> graph = graph(sagaId, orderId, now);
        try {
            inserts.insert(saga, graph);
            return sagaId;
        } catch (DataIntegrityViolationException conflict) {
            return sagas.findByTenantIdAndOrderId(tenantId, orderId)
                    .orElseThrow(() -> conflict).getId();
        }
    }

    @Transactional
    public List<OrderSagaClaim> claimBatch() {
        Instant now = clock.instant();
        List<OrderSagaEntity> values = sagas.lockReady(
                List.of(OrderSagaStatus.PENDING, OrderSagaStatus.RUNNING, OrderSagaStatus.COMPENSATING),
                now, PageRequest.of(0, properties.batchSize()));
        values.removeIf(value -> value.getAttemptCount() >= properties.maximumAttempts());
        values.forEach(value -> value.claim(now));
        return values.stream().map(value -> new OrderSagaClaim(
                value.getId(), value.getTenantId(), value.getOrderId(), value.getActorId(),
                value.getCorrelationId(), value.getStatus(), value.getCurrentStepKey(),
                value.getAttemptCount())).toList();
    }

    @Transactional
    public OrderSagaStepEntity claimStep(String sagaId, String stepKey) {
        Instant now = clock.instant();
        OrderSagaEntity saga = sagas.findByIdForUpdate(sagaId)
                .orElseThrow(() -> new IllegalArgumentException("saga not found"));
        OrderSagaStepEntity step = steps.findBySagaIdAndStepKeyForUpdate(sagaId, stepKey)
                .orElseThrow(() -> new IllegalArgumentException("saga step not found"));
        if (step.getAttemptCount() >= properties.maximumAttempts()) {
            step.manualReview("MAXIMUM_ATTEMPTS_EXCEEDED", "Saga step exhausted retry attempts", now);
            saga.manualReview("MAXIMUM_ATTEMPTS_EXCEEDED", "Saga step exhausted retry attempts", now);
            return step;
        }
        step.claim(now);
        saga.focus(stepKey, now);
        return step;
    }

    @Transactional
    public void markStepSucceeded(String sagaId, String stepKey, String externalReferenceId) {
        Instant now = clock.instant();
        OrderSagaEntity saga = sagas.findByIdForUpdate(sagaId)
                .orElseThrow(() -> new IllegalArgumentException("saga not found"));
        steps.findBySagaIdAndStepKeyForUpdate(sagaId, stepKey)
                .orElseThrow(() -> new IllegalArgumentException("saga step not found"))
                .succeed(externalReferenceId, now);
        saga.resume(now);
    }

    @Transactional
    public void markStepCompensated(String sagaId, String stepKey) {
        Instant now = clock.instant();
        OrderSagaEntity saga = sagas.findByIdForUpdate(sagaId)
                .orElseThrow(() -> new IllegalArgumentException("saga not found"));
        steps.findBySagaIdAndStepKeyForUpdate(sagaId, stepKey)
                .orElseThrow(() -> new IllegalArgumentException("saga step not found"))
                .compensated(now);
        saga.resume(now);
    }

    @Transactional
    public void markStepFailed(String sagaId, String stepKey, String errorCode, String errorMessage) {
        markForwardStepFailed(sagaId, stepKey, errorCode, errorMessage);
    }

    @Transactional
    public OrderSagaFailureAction markForwardStepFailed(
            String sagaId,
            String stepKey,
            String errorCode,
            String errorMessage
    ) {
        Instant now = clock.instant();
        OrderSagaEntity saga = sagas.findByIdForUpdate(sagaId)
                .orElseThrow(() -> new IllegalArgumentException("saga not found"));
        OrderSagaStepEntity step = steps.findBySagaIdAndStepKeyForUpdate(sagaId, stepKey)
                .orElseThrow(() -> new IllegalArgumentException("saga step not found"));
        int nextAttempt = step.getAttemptCount() + 1;
        if (nextAttempt >= properties.maximumAttempts()) {
            step.fail(now, errorCode, errorMessage, now);
            beginCompensationLocked(
                    saga,
                    steps.findBySagaIdForUpdate(sagaId),
                    OrderSagaTerminalIntent.FAILED,
                    errorMessage,
                    now
            );
            return saga.getStatus() == OrderSagaStatus.MANUAL_REVIEW
                    ? OrderSagaFailureAction.MANUAL_REVIEW
                    : OrderSagaFailureAction.COMPENSATION_STARTED;
        }
        Instant next = now.plus(retryDelay(nextAttempt));
        step.fail(next, errorCode, errorMessage, now);
        saga.retry(next, errorCode, errorMessage, now);
        return OrderSagaFailureAction.RETRY_SCHEDULED;
    }

    @Transactional
    public void beginCompensation(String sagaId) {
        Instant now = clock.instant();
        OrderSagaEntity saga = sagas.findByIdForUpdate(sagaId)
                .orElseThrow(() -> new IllegalArgumentException("saga not found"));
        beginCompensationLocked(
                saga,
                steps.findBySagaIdForUpdate(sagaId),
                OrderSagaTerminalIntent.FAILED,
                "Forward saga failed",
                now
        );
    }

    @Transactional
    public void beginCancellationCompensation(String sagaId, String reason) {
        Instant now = clock.instant();
        OrderSagaEntity saga = sagas.findByIdForUpdate(sagaId)
                .orElseThrow(() -> new IllegalArgumentException("saga not found"));
        beginCompensationLocked(
                saga,
                steps.findBySagaIdForUpdate(sagaId),
                OrderSagaTerminalIntent.CANCELLED,
                reason,
                now
        );
    }

    @Transactional(readOnly = true)
    public String nextCompensationStepKey(String sagaId) {
        Instant now = clock.instant();
        return steps.findBySagaIdOrderBySequenceNoAsc(sagaId).stream()
                .filter(value -> value.getStepType().compensation())
                .filter(value -> value.getStatus() == OrderSagaStepStatus.PENDING
                        || value.getStatus() == OrderSagaStepStatus.FAILED)
                .filter(value -> !value.getNextAttemptAt().isAfter(now))
                .sorted(java.util.Comparator.comparingInt(OrderSagaStepEntity::getSequenceNo))
                .map(OrderSagaStepEntity::getStepKey)
                .findFirst()
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean compensationStepsCompleted(String sagaId) {
        return steps.findBySagaIdOrderBySequenceNoAsc(sagaId).stream()
                .filter(value -> value.getStepType().compensation())
                .allMatch(value -> value.getStatus() == OrderSagaStepStatus.COMPENSATED
                        || value.getStatus() == OrderSagaStepStatus.SKIPPED);
    }

    @Transactional(readOnly = true)
    public OrderSagaEntity saga(String sagaId) {
        return sagas.findById(sagaId)
                .orElseThrow(() -> new IllegalArgumentException("saga not found"));
    }

    @Transactional
    public OrderSagaFailureAction markCompensationStepFailed(
            String sagaId,
            String stepKey,
            String errorCode,
            String errorMessage
    ) {
        Instant now = clock.instant();
        OrderSagaEntity saga = sagas.findByIdForUpdate(sagaId)
                .orElseThrow(() -> new IllegalArgumentException("saga not found"));
        OrderSagaStepEntity step = steps.findBySagaIdAndStepKeyForUpdate(sagaId, stepKey)
                .orElseThrow(() -> new IllegalArgumentException("saga step not found"));
        int nextAttempt = step.getAttemptCount() + 1;
        if (nextAttempt >= properties.maximumAttempts()
                || saga.compensationDeadlineExceeded(now)) {
            step.manualReview(errorCode, errorMessage, now);
            saga.manualReview(errorCode, errorMessage, now);
            return OrderSagaFailureAction.MANUAL_REVIEW;
        }
        Instant next = now.plus(retryDelay(nextAttempt));
        if (saga.getCompensationDeadlineAt() != null
                && next.isAfter(saga.getCompensationDeadlineAt())) {
            next = saga.getCompensationDeadlineAt();
        }
        step.fail(next, errorCode, errorMessage, now);
        saga.retry(next, errorCode, errorMessage, now);
        return OrderSagaFailureAction.RETRY_SCHEDULED;
    }

    @Transactional
    public void markCompleted(String sagaId) {
        sagas.findByIdForUpdate(sagaId)
                .orElseThrow(() -> new IllegalArgumentException("saga not found"))
                .complete(clock.instant());
    }

    @Transactional
    public void markCompensated(String sagaId) {
        sagas.findByIdForUpdate(sagaId)
                .orElseThrow(() -> new IllegalArgumentException("saga not found"))
                .compensated(clock.instant());
    }

    @Transactional
    public int recoverStaleClaims() {
        Instant now = clock.instant();
        List<OrderSagaEntity> stale = sagas.lockStale(
                List.of(OrderSagaStatus.RUNNING, OrderSagaStatus.COMPENSATING),
                now.minus(properties.claimTimeout()), PageRequest.of(0, properties.batchSize()));
        for (OrderSagaEntity saga : stale) {
            saga.recover(now);
            for (OrderSagaStepEntity step : steps.findBySagaIdForUpdate(saga.getId())) {
                step.recover(now);
            }
        }
        return stale.size();
    }

    @Transactional(readOnly = true)
    public List<OrderSagaStepEntity> steps(String sagaId) {
        return steps.findBySagaIdOrderBySequenceNoAsc(sagaId);
    }

    @Transactional(readOnly = true)
    public String nextForwardStepKey(String sagaId) {
        Instant now = clock.instant();
        return steps.findBySagaIdOrderBySequenceNoAsc(sagaId).stream()
                .filter(value -> !value.getStepType().compensation())
                .filter(value -> value.getStatus() == OrderSagaStepStatus.PENDING
                        || value.getStatus() == OrderSagaStepStatus.FAILED)
                .filter(value -> !value.getNextAttemptAt().isAfter(now))
                .map(OrderSagaStepEntity::getStepKey)
                .findFirst()
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean forwardStepsCompleted(String sagaId) {
        return steps.findBySagaIdOrderBySequenceNoAsc(sagaId).stream()
                .filter(value -> !value.getStepType().compensation())
                .allMatch(value -> value.getStatus() == OrderSagaStepStatus.SUCCEEDED);
    }

    private void beginCompensationLocked(
            OrderSagaEntity saga,
            List<OrderSagaStepEntity> graph,
            OrderSagaTerminalIntent requestedIntent,
            String reason,
            Instant now
    ) {
        boolean financePosted = graph.stream().anyMatch(value ->
                value.getStepType() == OrderSagaStepType.POST_FINANCE
                        && value.getStatus() == OrderSagaStepStatus.SUCCEEDED);
        java.util.Set<String> committedLines = graph.stream()
                .filter(value -> value.getStepType() == OrderSagaStepType.COMMIT_STOCK)
                .filter(value -> value.getStatus() == OrderSagaStepStatus.SUCCEEDED)
                .map(OrderSagaStepEntity::getOrderLineId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Set<String> reservedLines = graph.stream()
                .filter(value -> value.getStepType() == OrderSagaStepType.RESERVE_STOCK)
                .filter(value -> value.getStatus() == OrderSagaStepStatus.SUCCEEDED)
                .map(OrderSagaStepEntity::getOrderLineId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        OrderSagaTerminalIntent terminalIntent = committedLines.isEmpty()
                ? requestedIntent
                : OrderSagaTerminalIntent.MANUAL_REVIEW;
        String compensationReason = committedLines.isEmpty()
                ? reason
                : "Partial inventory commit requires manual reconciliation: " + reason;
        saga.beginCompensation(
                terminalIntent,
                compensationReason,
                now.plus(properties.compensationDeadline()),
                now
        );
        for (OrderSagaStepEntity value : graph) {
            if (!value.getStepType().compensation()) continue;
            boolean required = value.getStepType() == OrderSagaStepType.REVERSE_FINANCE
                    ? financePosted
                    : reservedLines.contains(value.getOrderLineId())
                            && !committedLines.contains(value.getOrderLineId());
            if (required && value.getStatus() == OrderSagaStepStatus.SKIPPED) {
                value.activateCompensation(now);
            } else if (!required) {
                value.keepSkipped(now);
            }
        }
    }

    private List<OrderSagaStepEntity> graph(String sagaId, String orderId, Instant now) {
        var orderLineValues = orderLines.findByOrderIdOrderByLineNo(orderId);
        if (orderLineValues.isEmpty()) throw new IllegalStateException("order must contain at least one line");
        List<OrderSagaStepEntity> result = new ArrayList<>();
        int sequence = 0;
        for (var line : orderLineValues) {
            result.add(step(sagaId, orderId, "reserve:" + line.getLineNo(), sequence++,
                    OrderSagaStepType.RESERVE_STOCK, line.getId(), now));
        }
        result.add(step(sagaId, orderId, "finance", sequence++,
                OrderSagaStepType.POST_FINANCE, null, now));
        for (var line : orderLineValues) {
            result.add(step(sagaId, orderId, "commit:" + line.getLineNo(), sequence++,
                    OrderSagaStepType.COMMIT_STOCK, line.getId(), now));
        }
        result.add(step(sagaId, orderId, "reverse-finance", sequence++,
                OrderSagaStepType.REVERSE_FINANCE, null, now));
        for (int index = orderLineValues.size() - 1; index >= 0; index--) {
            var line = orderLineValues.get(index);
            result.add(step(sagaId, orderId, "release:" + line.getLineNo(), sequence++,
                    OrderSagaStepType.RELEASE_STOCK, line.getId(), now));
        }
        return List.copyOf(result);
    }

    private OrderSagaStepEntity step(String sagaId, String orderId, String key, int sequence,
            OrderSagaStepType type, String lineId, Instant now) {
        return new OrderSagaStepEntity(UUID.randomUUID().toString(), sagaId, key, sequence,
                type, lineId, orderId + ":" + key, now);
    }

    private Duration retryDelay(int attempt) {
        long initial = properties.initialRetry().toMillis();
        long maximum = properties.maximumRetry().toMillis();
        long calculated = initial * (1L << Math.min(16, Math.max(0, attempt - 1)));
        return Duration.ofMillis(Math.min(maximum, calculated));
    }
}
