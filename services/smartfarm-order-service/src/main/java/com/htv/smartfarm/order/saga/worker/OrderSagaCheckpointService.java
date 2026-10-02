package com.htv.smartfarm.order.saga.worker;

import java.time.Clock;

import com.htv.smartfarm.order.domain.OrderEntity;
import com.htv.smartfarm.order.domain.OrderJpaRepository;
import com.htv.smartfarm.order.domain.OrderLineEntity;
import com.htv.smartfarm.order.domain.OrderLineJpaRepository;
import com.htv.smartfarm.order.outbox.OrderEventStore;
import com.htv.smartfarm.order.saga.persistence.OrderSagaClaim;
import com.htv.smartfarm.order.saga.persistence.OrderSagaStepEntity;
import com.htv.smartfarm.order.saga.persistence.OrderSagaStepType;
import com.htv.smartfarm.order.saga.persistence.OrderSagaTransactionService;
import com.htv.smartfarm.order.saga.persistence.OrderSagaTerminalIntent;
import com.htv.smartfarm.order.outbox.OrderOutboxEntity;
import com.htv.smartfarm.order.outbox.OrderOutboxJpaRepository;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderSagaCheckpointService {
    private final OrderJpaRepository orders;
    private final OrderLineJpaRepository lines;
    private final OrderSagaTransactionService transactions;
    private final Clock clock;
    private final OrderOutboxJpaRepository outbox;
    private final OrderEventStore eventStore;

    public OrderSagaCheckpointService(OrderJpaRepository orders, OrderLineJpaRepository lines,
                                      OrderSagaTransactionService transactions, Clock clock,
                                      OrderOutboxJpaRepository outbox,
                                      OrderEventStore eventStore) {
        this.orders = orders;
        this.lines = lines;
        this.transactions = transactions;
        this.clock = clock;
        this.outbox = outbox;
        this.eventStore = eventStore;
    }

    @Transactional
    public void started(OrderSagaClaim claim, OrderSagaStepEntity step) {
        OrderEntity order = orders.findByTenantIdAndIdForUpdate(claim.tenantId(), claim.orderId()).orElseThrow();
        if (step.getStepType() == OrderSagaStepType.RESERVE_STOCK && order.domainStatus() == com.htv.smartfarm.order.domain.OrderDomainStatus.CREATED) { order.markStockReserving(claim.actorId(), clock.millis()); eventStore.append(order, claim.correlationId()); }
        else if (step.getStepType() == OrderSagaStepType.POST_FINANCE && order.domainStatus() == com.htv.smartfarm.order.domain.OrderDomainStatus.STOCK_RESERVED) { order.markFinancePosting(claim.actorId(), clock.millis()); eventStore.append(order, claim.correlationId()); }
    }

    @Transactional
    public void compensationStarted(OrderSagaClaim claim) {
        OrderEntity order = orders.findByTenantIdAndIdForUpdate(claim.tenantId(), claim.orderId()).orElseThrow();
        if (order.domainStatus() != com.htv.smartfarm.order.domain.OrderDomainStatus.COMPENSATING) { order.markCompensating("Order saga compensation", claim.actorId(), clock.millis()); eventStore.append(order, claim.correlationId()); }
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
            if (allReserved) order.markStockReserved(claim.actorId(), clock.millis());
        } else if (step.getStepType() == OrderSagaStepType.POST_FINANCE) {
            order.setExpenseTxnId(required(result.externalReferenceId(), "expenseTransactionId"));
            order.markFinancePosted(claim.actorId(), clock.millis());
        }
        transactions.markStepSucceeded(claim.sagaId(), step.getStepKey(), result.externalReferenceId());
    }

    @Transactional
    public void completed(OrderSagaClaim claim) {
        OrderEntity order = orders.findByTenantIdAndIdForUpdate(claim.tenantId(), claim.orderId())
                .orElseThrow(() -> new IllegalStateException("order not found for saga completion"));
        order.complete(claim.actorId(), clock.millis());
        transactions.markCompleted(claim.sagaId());
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
    }

    @Transactional
    public void compensationManualReview(OrderSagaClaim claim, String reason) {
        OrderEntity order = orders.findByTenantIdAndIdForUpdate(claim.tenantId(), claim.orderId())
                .orElseThrow(() -> new IllegalStateException("order not found for manual review"));
        if (order.domainStatus() != com.htv.smartfarm.order.domain.OrderDomainStatus.MANUAL_REVIEW) {
            order.requireManualReview(reason, claim.actorId(), clock.millis());
            eventStore.append(order, claim.correlationId());
        }
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalStateException(field + " is missing");
        return value.trim();
    }
}
