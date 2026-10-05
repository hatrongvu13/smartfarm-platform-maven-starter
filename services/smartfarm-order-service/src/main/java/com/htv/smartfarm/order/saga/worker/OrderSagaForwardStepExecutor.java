package com.htv.smartfarm.order.saga.worker;

import java.util.concurrent.TimeUnit;

import com.htv.smartfarm.order.domain.OrderEntity;
import com.htv.smartfarm.order.domain.OrderJpaRepository;
import com.htv.smartfarm.order.domain.OrderLineEntity;
import com.htv.smartfarm.order.domain.OrderLineJpaRepository;
import com.htv.smartfarm.order.grpc.ServiceTokenClient;
import com.htv.smartfarm.order.saga.persistence.OrderSagaClaim;
import com.htv.smartfarm.order.saga.persistence.OrderSagaStepEntity;
import com.htv.smartfarm.proto.common.v1.Money;
import com.htv.smartfarm.proto.common.v1.Quantity;
import com.htv.smartfarm.proto.common.v1.RequestContext;
import com.htv.smartfarm.proto.finance.v1.FarmFinanceServiceGrpc;
import com.htv.smartfarm.proto.finance.v1.FinanceTransaction;
import com.htv.smartfarm.proto.finance.v1.RecordExpenseRequest;
import com.htv.smartfarm.proto.finance.v1.TransactionKind;
import com.htv.smartfarm.proto.inventory.v1.CommitReservationRequest;
import com.htv.smartfarm.proto.inventory.v1.InventoryServiceGrpc;
import com.htv.smartfarm.proto.inventory.v1.ReserveStockRequest;
import com.htv.smartfarm.security.grpc.BearerCallCredentials;

import org.springframework.stereotype.Service;

@Service
public class OrderSagaForwardStepExecutor {
    private static final String INVENTORY_AUDIENCE = "smartfarm-inventory";
    private static final String FINANCE_AUDIENCE = "smartfarm-finance";

    private final OrderJpaRepository orders;
    private final OrderLineJpaRepository lines;
    private final InventoryServiceGrpc.InventoryServiceBlockingStub inventory;
    private final FarmFinanceServiceGrpc.FarmFinanceServiceBlockingStub finance;
    private final ServiceTokenClient tokens;
    private final long inventoryDeadlineMillis;
    private final long financeDeadlineMillis;

    public OrderSagaForwardStepExecutor(OrderJpaRepository orders, OrderLineJpaRepository lines,
            InventoryServiceGrpc.InventoryServiceBlockingStub inventory,
            FarmFinanceServiceGrpc.FarmFinanceServiceBlockingStub finance,
            ServiceTokenClient tokens,
            @org.springframework.beans.factory.annotation.Value("${smartfarm.order.grpc.inventory-deadline:5s}") java.time.Duration inventoryDeadline,
            @org.springframework.beans.factory.annotation.Value("${smartfarm.order.grpc.finance-deadline:5s}") java.time.Duration financeDeadline) {
        this.orders = orders; this.lines = lines; this.inventory = inventory;
        this.finance = finance; this.tokens = tokens;
        this.inventoryDeadlineMillis = positive(inventoryDeadline, "inventory deadline");
        this.financeDeadlineMillis = positive(financeDeadline, "finance deadline");
    }

    public OrderSagaStepResult execute(OrderSagaClaim saga, OrderSagaStepEntity step) {
        OrderEntity order = orders.findByTenantIdAndId(saga.tenantId(), saga.orderId())
                .orElseThrow(() -> new IllegalStateException("order not found for saga"));
        return switch (step.getStepType()) {
            case RESERVE_STOCK -> reserve(saga, order, step, line(step));
            case POST_FINANCE -> finance(saga, order, step);
            case COMMIT_STOCK -> commit(saga, order, step, line(step));
            case REVERSE_FINANCE, RELEASE_STOCK ->
                    throw new IllegalStateException("compensation step cannot run in forward worker");
        };
    }

    private OrderSagaStepResult reserve(OrderSagaClaim saga, OrderEntity order,
            OrderSagaStepEntity step, OrderLineEntity line) {
        var response = inventory(order, saga.actorId()).reserveStock(ReserveStockRequest.newBuilder()
                .setContext(context(saga, step))
                .setOrderId(order.getId())
                .setItemId(line.getItemId())
                .setWarehouseId(line.getWarehouseId())
                .setQuantity(Quantity.newBuilder()
                        .setDecimalValue(line.getQuantity()).setUnit(line.getQuantityUnit()))
                .build());
        return new OrderSagaStepResult(response.getReservation().getReservationId());
    }

    private OrderSagaStepResult finance(OrderSagaClaim saga, OrderEntity order, OrderSagaStepEntity step) {
        var response = finance(order, saga.actorId()).recordExpense(RecordExpenseRequest.newBuilder()
                .setContext(context(saga, step))
                .setTransaction(FinanceTransaction.newBuilder()
                        .setFarmId(order.getFarmId())
                        .setBatchId(order.getBatchId() == null ? "" : order.getBatchId())
                        .setKind(TransactionKind.TRANSACTION_KIND_EXPENSE)
                        .setAmount(Money.newBuilder()
                                .setCurrencyCode(order.getCurrencyCode())
                                .setMinorUnits(order.getTotalMinor()))
                        .setCategory("ORDER")
                        .setReferenceType("ORDER")
                        .setReferenceId(order.getId()))
                .build());
        return new OrderSagaStepResult(response.getTransaction().getTransactionId());
    }

    private OrderSagaStepResult commit(OrderSagaClaim saga, OrderEntity order,
            OrderSagaStepEntity step, OrderLineEntity line) {
        if (line.getReservationId() == null || line.getReservationId().isBlank()) {
            throw new IllegalStateException("reservation id is missing for commit");
        }
        inventory(order, saga.actorId()).commitReservation(CommitReservationRequest.newBuilder()
                .setContext(context(saga, step))
                .setReservationId(line.getReservationId())
                .build());
        return OrderSagaStepResult.withoutReference();
    }

    private OrderLineEntity line(OrderSagaStepEntity step) {
        if (step.getOrderLineId() == null) throw new IllegalStateException("order line id is missing");
        return lines.findById(step.getOrderLineId())
                .orElseThrow(() -> new IllegalStateException("order line not found for saga step"));
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
