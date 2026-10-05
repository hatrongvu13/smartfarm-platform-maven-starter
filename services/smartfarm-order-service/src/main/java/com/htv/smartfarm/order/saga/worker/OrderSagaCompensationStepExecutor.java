package com.htv.smartfarm.order.saga.worker;

import java.util.concurrent.TimeUnit;

import com.htv.smartfarm.order.domain.OrderEntity;
import com.htv.smartfarm.order.domain.OrderJpaRepository;
import com.htv.smartfarm.order.domain.OrderLineEntity;
import com.htv.smartfarm.order.domain.OrderLineJpaRepository;
import com.htv.smartfarm.order.grpc.ServiceTokenClient;
import com.htv.smartfarm.order.saga.persistence.OrderSagaClaim;
import com.htv.smartfarm.order.saga.persistence.OrderSagaStepEntity;
import com.htv.smartfarm.proto.common.v1.RequestContext;
import com.htv.smartfarm.proto.finance.v1.FarmFinanceServiceGrpc;
import com.htv.smartfarm.proto.finance.v1.ReverseExpenseRequest;
import com.htv.smartfarm.proto.inventory.v1.InventoryServiceGrpc;
import com.htv.smartfarm.proto.inventory.v1.ReleaseReservationRequest;
import com.htv.smartfarm.security.grpc.BearerCallCredentials;

import org.springframework.stereotype.Service;

@Service
public class OrderSagaCompensationStepExecutor {
    private static final String INVENTORY_AUDIENCE = "smartfarm-inventory";
    private static final String FINANCE_AUDIENCE = "smartfarm-finance";

    private final OrderJpaRepository orders;
    private final OrderLineJpaRepository lines;
    private final InventoryServiceGrpc.InventoryServiceBlockingStub inventory;
    private final FarmFinanceServiceGrpc.FarmFinanceServiceBlockingStub finance;
    private final ServiceTokenClient tokens;
    private final long inventoryDeadlineMillis;
    private final long financeDeadlineMillis;

    public OrderSagaCompensationStepExecutor(
            OrderJpaRepository orders,
            OrderLineJpaRepository lines,
            InventoryServiceGrpc.InventoryServiceBlockingStub inventory,
            FarmFinanceServiceGrpc.FarmFinanceServiceBlockingStub finance,
            ServiceTokenClient tokens,
            @org.springframework.beans.factory.annotation.Value("${smartfarm.order.grpc.inventory-deadline:5s}") java.time.Duration inventoryDeadline,
            @org.springframework.beans.factory.annotation.Value("${smartfarm.order.grpc.finance-deadline:5s}") java.time.Duration financeDeadline
    ) {
        this.orders = orders;
        this.lines = lines;
        this.inventory = inventory;
        this.finance = finance;
        this.tokens = tokens;
        this.inventoryDeadlineMillis = positive(inventoryDeadline, "inventory deadline");
        this.financeDeadlineMillis = positive(financeDeadline, "finance deadline");
    }

    public void execute(OrderSagaClaim saga, OrderSagaStepEntity step) {
        OrderEntity order = orders.findByTenantIdAndId(saga.tenantId(), saga.orderId())
                .orElseThrow(() -> new IllegalStateException("order not found for compensation"));
        switch (step.getStepType()) {
            case REVERSE_FINANCE -> reverseFinance(saga, order, step);
            case RELEASE_STOCK -> releaseStock(saga, order, step, line(step));
            default -> throw new IllegalStateException("forward step cannot run in compensation worker");
        }
    }

    private void reverseFinance(OrderSagaClaim saga, OrderEntity order, OrderSagaStepEntity step) {
        finance(order, saga.actorId()).reverseExpense(ReverseExpenseRequest.newBuilder()
                .setContext(context(saga, step))
                .setOriginalExpenseKey(order.getId() + ":finance")
                .setReversalKey(step.getIdempotencyKey())
                .build());
    }

    private void releaseStock(
            OrderSagaClaim saga,
            OrderEntity order,
            OrderSagaStepEntity step,
            OrderLineEntity line
    ) {
        if (line.getReservationId() == null || line.getReservationId().isBlank()) {
            throw new IllegalStateException("reservation id is missing for compensation");
        }
        inventory(order, saga.actorId()).releaseReservation(ReleaseReservationRequest.newBuilder()
                .setContext(context(saga, step))
                .setReservationId(line.getReservationId())
                .setReason("Order saga compensation")
                .build());
    }

    private OrderLineEntity line(OrderSagaStepEntity step) {
        if (step.getOrderLineId() == null) throw new IllegalStateException("order line id is missing");
        return lines.findById(step.getOrderLineId())
                .orElseThrow(() -> new IllegalStateException("order line not found for compensation"));
    }

    private RequestContext context(OrderSagaClaim saga, OrderSagaStepEntity step) {
        return RequestContext.newBuilder()
                .setTenantId(saga.tenantId())
                .setActorId(saga.actorId() == null ? "" : saga.actorId())
                .setCorrelationId(saga.correlationId() == null ? saga.orderId() : saga.correlationId())
                .setIdempotencyKey(step.getIdempotencyKey())
                .build();
    }

    private InventoryServiceGrpc.InventoryServiceBlockingStub inventory(OrderEntity order, String actor) {
        String token = tokens.tokenFor(INVENTORY_AUDIENCE, order.getTenantId(), actor);
        return inventory.withDeadlineAfter(inventoryDeadlineMillis, TimeUnit.MILLISECONDS)
                .withCallCredentials(new BearerCallCredentials(() -> token));
    }

    private FarmFinanceServiceGrpc.FarmFinanceServiceBlockingStub finance(OrderEntity order, String actor) {
        String token = tokens.tokenFor(FINANCE_AUDIENCE, order.getTenantId(), actor);
        return finance.withDeadlineAfter(financeDeadlineMillis, TimeUnit.MILLISECONDS)
                .withCallCredentials(new BearerCallCredentials(() -> token));
    }
    private static long positive(java.time.Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
        return value.toMillis();
    }
}
