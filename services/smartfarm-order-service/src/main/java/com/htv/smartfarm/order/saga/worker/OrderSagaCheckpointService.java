package com.htv.smartfarm.order.saga.worker;

import java.time.Clock;

import com.htv.smartfarm.order.domain.OrderEntity;
import com.htv.smartfarm.order.domain.OrderJpaRepository;
import com.htv.smartfarm.order.domain.OrderLineEntity;
import com.htv.smartfarm.order.domain.OrderLineJpaRepository;
import com.htv.smartfarm.order.outbox.OrderEventStore;
import com.htv.smartfarm.order.outbox.OrderBusinessEventContext;
import com.htv.smartfarm.order.outbox.OrderBusinessEventType;
import com.htv.smartfarm.order.saga.persistence.OrderSagaClaim;
import com.htv.smartfarm.order.saga.persistence.OrderSagaStepEntity;
import com.htv.smartfarm.order.saga.persistence.OrderSagaStepType;
import com.htv.smartfarm.order.saga.persistence.OrderSagaTransactionService;
import com.htv.smartfarm.order.saga.persistence.OrderSagaTerminalIntent;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderSagaCheckpointService {
    private final OrderJpaRepository orders;
    private final OrderLineJpaRepository lines;
    private final OrderSagaTransactionService transactions;
    private final Clock clock;
    private final OrderEventStore eventStore;

    public OrderSagaCheckpointService(OrderJpaRepository orders, OrderLineJpaRepository lines,
                                      OrderSagaTransactionService transactions, Clock clock,
                                      OrderEventStore eventStore) {
        this.orders = orders;
        this.lines = lines;
        this.transactions = transactions;
        this.clock = clock;
        this.eventStore = eventStore;
    }

    @Transactional
    public void started(OrderSagaClaim claim, OrderSagaStepEntity step) {
        OrderEntity order = orders.findByTenantIdAndIdForUpdate(claim.tenantId(), claim.orderId()).orElseThrow();
        if (step.getStepType() == OrderSagaStepType.RESERVE_STOCK
                && order.domainStatus() == com.htv.smartfarm.order.domain.OrderDomainStatus.CREATED) {
            String previous = order.getStatus();
            order.markStockReserving(claim.actorId(), clock.millis());
            eventStore.append(order, claim.correlationId());
            eventStore.appendTransition(order, OrderBusinessEventType.STOCK_RESERVATION_REQUESTED,
                    claim.correlationId(), claim.actorId(), claim.sagaId(), step.getStepKey(),
                    previous, order.getStatus());
        }
        else if (step.getStepType() == OrderSagaStepType.POST_FINANCE
                && order.domainStatus() == com.htv.smartfarm.order.domain.OrderDomainStatus.STOCK_RESERVED) {
            String previous = order.getStatus();
            order.markFinancePosting(claim.actorId(), clock.millis());
            eventStore.append(order, claim.correlationId());
            eventStore.appendTransition(order, OrderBusinessEventType.FINANCE_POST_REQUESTED,
                    claim.correlationId(), claim.actorId(), claim.sagaId(), step.getStepKey(),
                    previous, order.getStatus());
        }
    }

    @Transactional
    public void compensationStarted(OrderSagaClaim claim) {
        OrderEntity order = orders.findByTenantIdAndIdForUpdate(claim.tenantId(), claim.orderId()).orElseThrow();
        if (order.domainStatus() != com.htv.smartfarm.order.domain.OrderDomainStatus.COMPENSATING) {
            String previous = order.getStatus();
            order.markCompensating("Order saga compensation", claim.actorId(), clock.millis());
            eventStore.append(order, claim.correlationId());
            eventStore.appendTransition(order, OrderBusinessEventType.COMPENSATION_STARTED,
                    claim.correlationId(), claim.actorId(), claim.sagaId(), null,
                    previous, order.getStatus());
        }
    }

    @Transactional
    public void succeeded(OrderSagaClaim claim, OrderSagaStepEntity step, OrderSagaStepResult result) {
        OrderEntity order = orders.findByTenantIdAndIdForUpdate(claim.tenantId(), claim.orderId())
                .orElseThrow(() -> new IllegalStateException("order not found for saga checkpoint"));
        if (step.getStepType() == OrderSagaStepType.RESERVE_STOCK) {
            OrderLineEntity line = lines.findById(step.getOrderLineId())
                    .orElseThrow(() -> new IllegalStateException("order line not found for checkpoint"));
            line.setReservationId(required(result.externalReferenceId(), "reservationId"));
            boolean allReserved = transactions.steps(claim.sagaId()).stream()
                    .filter(value -> value.getStepType() == OrderSagaStepType.RESERVE_STOCK)
                    .allMatch(value -> value.getStepKey().equals(step.getStepKey())
                            || value.getStatus() == com.htv.smartfarm.order.saga.persistence.OrderSagaStepStatus.SUCCEEDED);
            if (allReserved) {
                String previous = order.getStatus();
                order.markStockReserved(claim.actorId(), clock.millis());
                eventStore.append(order, claim.correlationId());
                eventStore.appendTransition(order, OrderBusinessEventType.STOCK_RESERVED,
                        claim.correlationId(), claim.actorId(), claim.sagaId(), step.getStepKey(),
                        previous, order.getStatus());
            }
        } else if (step.getStepType() == OrderSagaStepType.POST_FINANCE) {
            order.setExpenseTxnId(required(result.externalReferenceId(), "expenseTransactionId"));
            String previous = order.getStatus();
            order.markFinancePosted(claim.actorId(), clock.millis());
            eventStore.append(order, claim.correlationId());
            eventStore.appendTransition(order, OrderBusinessEventType.FINANCE_POSTED,
                    claim.correlationId(), claim.actorId(), claim.sagaId(), step.getStepKey(),
                    previous, order.getStatus());
        }
        transactions.markStepSucceeded(claim.sagaId(), step.getStepKey(), result.externalReferenceId());
    }

    @Transactional
    public void completed(OrderSagaClaim claim) {
        OrderEntity order = orders.findByTenantIdAndIdForUpdate(claim.tenantId(), claim.orderId())
                .orElseThrow(() -> new IllegalStateException("order not found for saga completion"));
        String previous = order.getStatus();
        order.complete(claim.actorId(), clock.millis());
        transactions.markCompleted(claim.sagaId());
        eventStore.append(order, claim.correlationId());
        eventStore.appendTransition(order, OrderBusinessEventType.COMPLETED,
                claim.correlationId(), claim.actorId(), claim.sagaId(), null,
                previous, order.getStatus());
    }

    @Transactional
    public void compensatedStep(OrderSagaClaim claim, OrderSagaStepEntity step) {
        transactions.markStepCompensated(claim.sagaId(), step.getStepKey());
    }

    @Transactional
    public void compensationCompleted(OrderSagaClaim claim) {
        var saga = transactions.saga(claim.sagaId());
        OrderEntity order = orders.findByTenantIdAndIdForUpdate(claim.tenantId(), claim.orderId())
                .orElseThrow(() -> new IllegalStateException("order not found for compensation completion"));
        String reason = saga.getCompensationReason();
        OrderSagaTerminalIntent intent = saga.getTerminalIntent();
        if (intent == OrderSagaTerminalIntent.CANCELLED) {
            order.cancel(reason, claim.actorId(), clock.millis());
        } else if (intent == OrderSagaTerminalIntent.MANUAL_REVIEW) {
            order.requireManualReview(reason, claim.actorId(), clock.millis());
        } else {
            order.fail(reason, claim.actorId(), clock.millis());
        }
        transactions.markCompensated(claim.sagaId());
        eventStore.append(order, claim.correlationId());
        eventStore.appendBusiness(order, OrderBusinessEventType.COMPENSATION_COMPLETED,
                claim.correlationId(), new OrderBusinessEventContext(
                        claim.actorId(), claim.sagaId(), null, "ORDER_STATUS_COMPENSATING",
                        order.getStatus(), null, reason));
        if (intent == OrderSagaTerminalIntent.CANCELLED) {
            eventStore.appendTransition(order, OrderBusinessEventType.CANCELLED,
                    claim.correlationId(), claim.actorId(), claim.sagaId(), null,
                    "ORDER_STATUS_COMPENSATING", order.getStatus());
        } else if (intent == OrderSagaTerminalIntent.MANUAL_REVIEW) {
            eventStore.appendBusiness(order, OrderBusinessEventType.MANUAL_REVIEW_REQUIRED,
                    claim.correlationId(), new OrderBusinessEventContext(
                            claim.actorId(), claim.sagaId(), null, "ORDER_STATUS_COMPENSATING",
                            order.getStatus(), "COMPENSATION_REQUIRES_REVIEW", reason));
        }
    }

    @Transactional
    public void compensationManualReview(OrderSagaClaim claim, String reason) {
        OrderEntity order = orders.findByTenantIdAndIdForUpdate(claim.tenantId(), claim.orderId())
                .orElseThrow(() -> new IllegalStateException("order not found for manual review"));
        if (order.domainStatus() != com.htv.smartfarm.order.domain.OrderDomainStatus.MANUAL_REVIEW) {
            order.requireManualReview(reason, claim.actorId(), clock.millis());
            eventStore.append(order, claim.correlationId());
            eventStore.appendBusiness(order, OrderBusinessEventType.MANUAL_REVIEW_REQUIRED,
                    claim.correlationId(), new OrderBusinessEventContext(
                            claim.actorId(), claim.sagaId(), null, null, order.getStatus(),
                            "COMPENSATION_FAILED", reason));
        }
    }

    @Transactional
    public void forwardFailed(OrderSagaClaim claim, OrderSagaStepEntity step,
            String errorCode, String message) {
        OrderEntity order = orders.findByTenantIdAndId(claim.tenantId(), claim.orderId())
                .orElseThrow(() -> new IllegalStateException("order not found for failure event"));
        OrderBusinessEventType type = step.getStepType() == OrderSagaStepType.POST_FINANCE
                ? OrderBusinessEventType.FINANCE_POST_FAILED
                : OrderBusinessEventType.STOCK_RESERVATION_FAILED;
        eventStore.appendBusiness(order, type, claim.correlationId(),
                new OrderBusinessEventContext(claim.actorId(), claim.sagaId(), step.getStepKey(),
                        order.getStatus(), order.getStatus(), errorCode, message));
    }

    @Transactional
    public void compensationFailed(OrderSagaClaim claim, OrderSagaStepEntity step,
            String errorCode, String message) {
        OrderEntity order = orders.findByTenantIdAndId(claim.tenantId(), claim.orderId())
                .orElseThrow(() -> new IllegalStateException("order not found for compensation failure event"));
        eventStore.appendBusiness(order, OrderBusinessEventType.COMPENSATION_FAILED,
                claim.correlationId(), new OrderBusinessEventContext(
                        claim.actorId(), claim.sagaId(), step.getStepKey(), order.getStatus(),
                        order.getStatus(), errorCode, message));
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalStateException(field + " is missing");
        return value.trim();
    }
}
