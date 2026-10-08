package com.htv.smartfarm.order.saga.administration;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.htv.smartfarm.order.saga.persistence.*;
import com.htv.smartfarm.order.domain.OrderEntity;
import com.htv.smartfarm.order.domain.OrderJpaRepository;
import com.htv.smartfarm.order.outbox.OrderEventStore;
import com.htv.smartfarm.order.outbox.OrderBusinessEventContext;
import com.htv.smartfarm.order.outbox.OrderBusinessEventType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderSagaAdministrationService {
    private final OrderSagaJpaRepository sagas;
    private final OrderSagaStepJpaRepository steps;
    private final OrderSagaRecoveryAuditRepository audit;
    private final OrderSagaTransactionService transactions;
    private final Clock clock;
    private final OrderJpaRepository orders;
    private final OrderEventStore eventStore;

    public OrderSagaAdministrationService(OrderSagaJpaRepository sagas,
            OrderSagaStepJpaRepository steps, OrderSagaRecoveryAuditRepository audit,
            OrderSagaTransactionService transactions, Clock clock,
            OrderJpaRepository orders, OrderEventStore eventStore) {
        this.sagas = sagas; this.steps = steps; this.audit = audit;
        this.transactions = transactions; this.clock = clock;
        this.orders = orders; this.eventStore = eventStore;
    }

    @Transactional(readOnly = true)
    public OrderSagaInspection inspect(String tenantId, String sagaId) {
        OrderSagaEntity saga = sagaForTenant(tenantId, sagaId);
        var stepValues = steps.findBySagaIdOrderBySequenceNoAsc(sagaId).stream()
                .map(value -> new OrderSagaInspection.Step(
                        value.getStepKey(), value.getSequenceNo(), value.getStepType().name(),
                        value.getStatus().name(), value.getOrderLineId(), value.getExternalReferenceId(),
                        value.getAttemptCount(), value.getNextAttemptAt(), value.getClaimedAt(),
                        value.getLastErrorCode(), value.getLastErrorMessage()))
                .toList();
        return new OrderSagaInspection(
                saga.getId(), saga.getTenantId(), saga.getOrderId(), saga.getStatus().name(),
                saga.getTerminalIntent() == null ? null : saga.getTerminalIntent().name(),
                saga.getCurrentStepKey(), saga.getAttemptCount(), saga.getNextAttemptAt(),
                saga.getClaimedAt(), saga.getCompensationDeadlineAt(), saga.getLastErrorCode(),
                saga.getLastErrorMessage(), stepValues);
    }

    @Transactional
    public OrderSagaInspection retryStep(String tenantId, String sagaId, String stepKey,
            String actorId, String reason) {
        Instant now = clock.instant();
        OrderSagaEntity saga = sagaForTenantForUpdate(tenantId, sagaId);
        OrderSagaStepEntity step = steps.findBySagaIdAndStepKeyForUpdate(sagaId, required(stepKey, "stepKey"))
                .orElseThrow(() -> new IllegalArgumentException("saga step not found"));
        String previous = step.getStatus().name();
        step.retryManually(now);
        if (saga.getStatus() == OrderSagaStatus.MANUAL_REVIEW
                || saga.getStatus() == OrderSagaStatus.FAILED) {
            saga.resumeManually(now);
        } else {
            saga.resume(now);
        }
        audit.save(new OrderSagaRecoveryAuditEntity(
                UUID.randomUUID().toString(), tenantId, sagaId, stepKey, required(actorId, "actorId"),
                OrderSagaRecoveryAction.RETRY_STEP, previous, step.getStatus().name(),
                required(reason, "reason"), now));
        return inspect(tenantId, sagaId);
    }

    @Transactional
    public OrderSagaInspection resumeSaga(String tenantId, String sagaId,
            String actorId, String reason) {
        Instant now = clock.instant();
        OrderSagaEntity saga = sagaForTenantForUpdate(tenantId, sagaId);
        String previous = saga.getStatus().name();
        saga.resumeManually(now);
        audit.save(new OrderSagaRecoveryAuditEntity(
                UUID.randomUUID().toString(), tenantId, sagaId, null, required(actorId, "actorId"),
                OrderSagaRecoveryAction.RESUME_SAGA, previous, saga.getStatus().name(),
                required(reason, "reason"), now));
        return inspect(tenantId, sagaId);
    }

    @Transactional
    public int recoverStale(String tenantId, String actorId, String reason) {
        required(tenantId, "tenantId"); required(actorId, "actorId"); required(reason, "reason");
        int recovered = transactions.recoverStaleClaims(tenantId);
        audit.save(new OrderSagaRecoveryAuditEntity(
                UUID.randomUUID().toString(), tenantId, "batch", null, actorId,
                OrderSagaRecoveryAction.RECOVER_STALE, "CLAIMED", "RECOVERED",
                reason + "; recovered=" + recovered, clock.instant()));
        return recovered;
    }

    @Transactional
    public OrderSagaInspection forceCompensate(String tenantId, String sagaId,
            String actorId, String reason) {
        OrderSagaEntity saga = sagaForTenantForUpdate(tenantId, sagaId);
        String previous = saga.getStatus().name();
        transactions.forceCompensation(sagaId, OrderSagaTerminalIntent.FAILED,
                required(reason, "reason"));
        audit(saga, actorId, OrderSagaRecoveryAction.FORCE_COMPENSATE,
                previous, OrderSagaStatus.COMPENSATING.name(), reason);
        return inspect(tenantId, sagaId);
    }

    @Transactional
    public OrderSagaInspection forceCancel(String tenantId, String sagaId,
            String actorId, String reason) {
        Instant now = clock.instant();
        OrderSagaEntity saga = sagaForTenantForUpdate(tenantId, sagaId);
        if (saga.getStatus() != OrderSagaStatus.MANUAL_REVIEW
                && saga.getStatus() != OrderSagaStatus.FAILED
                && saga.getStatus() != OrderSagaStatus.COMPENSATING) {
            throw new IllegalStateException("force cancel requires failed, compensating or manual-review saga");
        }
        String previous = saga.getStatus().name();
        OrderEntity order = orderForTenantForUpdate(tenantId, saga.getOrderId());
        order.cancelAdministratively(required(reason, "reason"), required(actorId, "actorId"), clock.millis());
        saga.forceCompensated(OrderSagaTerminalIntent.CANCELLED, reason, now);
        eventStore.append(order, saga.getCorrelationId());
        eventStore.appendBusiness(order, OrderBusinessEventType.CANCELLED,
                saga.getCorrelationId(), new OrderBusinessEventContext(actorId, sagaId,
                        null, null, order.getStatus(), "FORCE_CANCEL", reason));
        audit(saga, actorId, OrderSagaRecoveryAction.FORCE_CANCEL,
                previous, saga.getStatus().name(), reason);
        return inspect(tenantId, sagaId);
    }

    @Transactional
    public OrderSagaInspection forceComplete(String tenantId, String sagaId,
            String actorId, String reason) {
        Instant now = clock.instant();
        OrderSagaEntity saga = sagaForTenantForUpdate(tenantId, sagaId);
        if (saga.getStatus() != OrderSagaStatus.MANUAL_REVIEW
                && saga.getStatus() != OrderSagaStatus.FAILED) {
            throw new IllegalStateException("force complete requires failed or manual-review saga");
        }
        String previous = saga.getStatus().name();
        OrderEntity order = orderForTenantForUpdate(tenantId, saga.getOrderId());
        order.completeAdministratively(required(reason, "reason"), required(actorId, "actorId"), clock.millis());
        saga.forceComplete(now);
        eventStore.append(order, saga.getCorrelationId());
        eventStore.appendBusiness(order, OrderBusinessEventType.COMPLETED,
                saga.getCorrelationId(), new OrderBusinessEventContext(actorId, sagaId,
                        null, null, order.getStatus(), "FORCE_COMPLETE", reason));
        audit(saga, actorId, OrderSagaRecoveryAction.FORCE_COMPLETE,
                previous, saga.getStatus().name(), reason);
        return inspect(tenantId, sagaId);
    }

    @Transactional
    public OrderSagaInspection markManuallyResolved(String tenantId, String sagaId,
            String actorId, String reason) {
        Instant now = clock.instant();
        OrderSagaEntity saga = sagaForTenantForUpdate(tenantId, sagaId);
        String previous = saga.getStatus().name();
        saga.markManuallyResolved(required(reason, "reason"), now);
        OrderEntity order = orderForTenantForUpdate(tenantId, saga.getOrderId());
        if (order.domainStatus() == com.htv.smartfarm.order.domain.OrderDomainStatus.MANUAL_REVIEW) {
            order.resolveManualReview(reason, actorId, clock.millis());
            eventStore.append(order, saga.getCorrelationId());
            eventStore.appendBusiness(order, OrderBusinessEventType.MANUAL_REVIEW_RESOLVED,
                    saga.getCorrelationId(), new OrderBusinessEventContext(actorId, sagaId,
                            null, order.getStatus(), order.getStatus(),
                            "MANUALLY_RESOLVED", reason));
        }
        audit(saga, actorId, OrderSagaRecoveryAction.MARK_MANUALLY_RESOLVED,
                previous, saga.getStatus().name(), reason);
        return inspect(tenantId, sagaId);
    }

    private OrderEntity orderForTenantForUpdate(String tenantId, String orderId) {
        return orders.findByTenantIdAndIdForUpdate(required(tenantId, "tenantId"), orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found"));
    }

    private void audit(OrderSagaEntity saga, String actorId,
            OrderSagaRecoveryAction action, String previous, String next, String reason) {
        audit.save(new OrderSagaRecoveryAuditEntity(UUID.randomUUID().toString(),
                saga.getTenantId(), saga.getId(), null, required(actorId, "actorId"),
                action, previous, next, required(reason, "reason"), clock.instant()));
    }

    private OrderSagaEntity sagaForTenant(String tenantId, String sagaId) {
        return sagas.findById(sagaId)
                .filter(value -> value.getTenantId().equals(required(tenantId, "tenantId")))
                .orElseThrow(() -> new IllegalArgumentException("saga not found"));
    }
    private OrderSagaEntity sagaForTenantForUpdate(String tenantId, String sagaId) {
        return sagas.findByIdForUpdate(sagaId)
                .filter(value -> value.getTenantId().equals(required(tenantId, "tenantId")))
                .orElseThrow(() -> new IllegalArgumentException("saga not found"));
    }
    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
