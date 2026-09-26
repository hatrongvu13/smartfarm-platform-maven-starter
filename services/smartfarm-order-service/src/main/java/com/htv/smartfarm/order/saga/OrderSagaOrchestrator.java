package com.htv.smartfarm.order.saga;

import com.htv.smartfarm.order.domain.OrderEntity;
import com.htv.smartfarm.order.domain.OrderJpaRepository;
import com.htv.smartfarm.security.grpc.BearerCallCredentials;
import com.htv.smartfarm.order.grpc.ServiceTokenClient;
import com.htv.smartfarm.proto.common.v1.Money;
import com.htv.smartfarm.proto.common.v1.Quantity;
import com.htv.smartfarm.proto.common.v1.RequestContext;
import com.htv.smartfarm.proto.finance.v1.*;
import com.htv.smartfarm.proto.inventory.v1.*;

import java.util.concurrent.TimeUnit;

import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestration-based Saga for placing a farm order.
 *
 * <p>Forward path (each step advances the persisted saga state):
 * <ol>
 *   <li>ReserveStock (inventory)   -> ORDER_STATUS_STOCK_RESERVED</li>
 *   <li>RecordExpense (finance)    -> ORDER_STATUS_FINANCE_POSTED</li>
 *   <li>CommitReservation (inventory) -> ORDER_STATUS_COMPLETED</li>
 * </ol>
 *
 * <p>If a step fails, the orchestrator runs the COMPENSATING actions for the steps that
 * already succeeded, in reverse order, and marks the order ORDER_STATUS_FAILED:
 * <ul>
 *   <li>finance posted  -> reverseExpense (offsetting entry)</li>
 *   <li>stock reserved  -> ReleaseReservation</li>
 * </ul>
 * Every downstream call carries a per-service token (Phase 2) scoped to that audience and
 * the human actor_id in RequestContext for end-to-end audit traceability. Steps are keyed by
 * the order id so they are idempotent under retry.
 */
@Service
public class OrderSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaOrchestrator.class);
    private static final Logger AUDIT = LoggerFactory.getLogger("smartfarm.audit.order");

    private static final String INVENTORY_AUDIENCE = "smartfarm-inventory";
    private static final String FINANCE_AUDIENCE = "smartfarm-finance";

    private final OrderJpaRepository orders;
    private final InventoryServiceGrpc.InventoryServiceBlockingStub inventory;
    private final FarmFinanceServiceGrpc.FarmFinanceServiceBlockingStub finance;
    private final ServiceTokenClient tokens;

    public OrderSagaOrchestrator(OrderJpaRepository orders,
                                 InventoryServiceGrpc.InventoryServiceBlockingStub inventory,
                                 FarmFinanceServiceGrpc.FarmFinanceServiceBlockingStub finance,
                                 ServiceTokenClient tokens) {
        this.orders = orders;
        this.inventory = inventory;
        this.finance = finance;
        this.tokens = tokens;
    }

    private RequestContext ctx(OrderEntity o, String actor, String stepKey) {
        return RequestContext.newBuilder()
                .setTenantId(o.getTenantId())
                .setActorId(actor == null ? "" : actor)
                .setCorrelationId(o.getId())
                .setIdempotencyKey(o.getId() + ":" + stepKey)
                .build();
    }

    private InventoryServiceGrpc.InventoryServiceBlockingStub inv(OrderEntity o, String actor) {
        String token = tokens.tokenFor(INVENTORY_AUDIENCE, o.getTenantId(), actor);
        return inventory.withDeadlineAfter(5, TimeUnit.SECONDS).withCallCredentials(new BearerCallCredentials(() -> token));
    }

    private FarmFinanceServiceGrpc.FarmFinanceServiceBlockingStub fin(OrderEntity o, String actor) {
        String token = tokens.tokenFor(FINANCE_AUDIENCE, o.getTenantId(), actor);
        return finance.withDeadlineAfter(5, TimeUnit.SECONDS).withCallCredentials(new BearerCallCredentials(() -> token));
    }

    /**
     * Drive the saga to a terminal state. The order row already exists in ORDER_STATUS_CREATED.
     * Returns the updated order. Never throws for a business failure — it records FAILED and
     * compensates; it only propagates truly unexpected errors after best-effort compensation.
     */
    public OrderEntity run(OrderEntity order, String actor) {
        try {
            // --- Step 1: reserve stock -------------------------------------
            var reserveResp = inv(order, actor).reserveStock(ReserveStockRequest.newBuilder()
                    .setContext(ctx(order, actor, "reserve"))
                    .setOrderId(order.getId())
                    .setItemId(order.getItemId())
                    .setWarehouseId(order.getWarehouseId())
                    .setQuantity(Quantity.newBuilder().setDecimalValue(order.getQuantity()).setUnit(order.getQuantityUnit()))
                    .build());
            String reservationId = reserveResp.getReservation().getReservationId();
            order.setReservationId(reservationId);
            order.setStatus("ORDER_STATUS_STOCK_RESERVED");
            orders.save(order);
            AUDIT.info("order_step ok step=reserve order_id={} tenant={} actor_id={} reservation_id={}",
                    order.getId(), order.getTenantId(), safe(actor), reservationId);

            // --- Step 2: post finance expense ------------------------------
            var txn = fin(order, actor).recordExpense(RecordExpenseRequest.newBuilder()
                    .setContext(ctx(order, actor, "expense"))
                    .setTransaction(FinanceTransaction.newBuilder()
                            .setFarmId(order.getFarmId())
                            .setBatchId(order.getBatchId() == null ? "" : order.getBatchId())
                            .setKind(TransactionKind.TRANSACTION_KIND_EXPENSE)
                            .setAmount(Money.newBuilder().setCurrencyCode(order.getCurrencyCode()).setMinorUnits(order.getTotalMinor()))
                            .setCategory("ORDER")
                            .setReferenceType("ORDER")
                            .setReferenceId(order.getId()))
                    .build());
            order.setExpenseTxnId(txn.getTransaction().getTransactionId());
            order.setStatus("ORDER_STATUS_FINANCE_POSTED");
            orders.save(order);
            AUDIT.info("order_step ok step=expense order_id={} tenant={} actor_id={} txn_id={}",
                    order.getId(), order.getTenantId(), safe(actor), txn.getTransaction().getTransactionId());

            // --- Step 3: commit the reservation (consume stock) ------------
            inv(order, actor).commitReservation(CommitReservationRequest.newBuilder()
                    .setContext(ctx(order, actor, "commit"))
                    .setReservationId(reservationId)
                    .build());
            order.setStatus("ORDER_STATUS_COMPLETED");
            orders.save(order);
            AUDIT.info("order_completed order_id={} tenant={} actor_id={}", order.getId(), order.getTenantId(), safe(actor));
            return order;

        } catch (StatusRuntimeException e) {
            log.warn("Saga step failed for order {}: {} {}", order.getId(), e.getStatus().getCode(), e.getStatus().getDescription());
            compensate(order, actor, describe(e));
            return order;
        } catch (RuntimeException e) {
            log.error("Unexpected saga error for order {}", order.getId(), e);
            compensate(order, actor, "internal error");
            return order;
        }
    }

    /** Run compensating actions in reverse for whatever forward steps had succeeded. */
    private void compensate(OrderEntity order, String actor, String reason) {
        // Reverse finance if it was posted.
        if (order.getExpenseTxnId() != null) {
            try {
                fin(order, actor).recordExpense(RecordExpenseRequest.newBuilder()
                        .setContext(ctx(order, actor, "expense-reversal"))
                        .setTransaction(FinanceTransaction.newBuilder()
                                .setFarmId(order.getFarmId())
                                .setKind(TransactionKind.TRANSACTION_KIND_INCOME)
                                .setAmount(Money.newBuilder().setCurrencyCode(order.getCurrencyCode()).setMinorUnits(order.getTotalMinor()))
                                .setCategory("SAGA_COMPENSATION")
                                .setReferenceType("ORDER_REVERSAL")
                                .setReferenceId(order.getId()))
                        .build());
                AUDIT.info("order_compensate step=reverse-expense order_id={} tenant={} actor_id={}",
                        order.getId(), order.getTenantId(), safe(actor));
            } catch (RuntimeException ex) {
                log.error("Compensation (reverse expense) failed for order {}", order.getId(), ex);
            }
        }
        // Release the reservation if it was held (and not already committed).
        if (order.getReservationId() != null && !"ORDER_STATUS_COMPLETED".equals(order.getStatus())) {
            try {
                inv(order, actor).releaseReservation(ReleaseReservationRequest.newBuilder()
                        .setContext(ctx(order, actor, "release"))
                        .setReservationId(order.getReservationId())
                        .setReason(reason)
                        .build());
                AUDIT.info("order_compensate step=release order_id={} tenant={} actor_id={}",
                        order.getId(), order.getTenantId(), safe(actor));
            } catch (RuntimeException ex) {
                log.error("Compensation (release reservation) failed for order {}", order.getId(), ex);
            }
        }
        order.setStatus("ORDER_STATUS_FAILED");
        order.setFailureReason(reason);
        orders.save(order);
        AUDIT.warn("order_failed order_id={} tenant={} actor_id={} reason={}",
                order.getId(), order.getTenantId(), safe(actor), reason);
    }

    private static String describe(StatusRuntimeException e) {
        String d = e.getStatus().getDescription();
        return e.getStatus().getCode() + (d == null || d.isBlank() ? "" : ": " + d);
    }

    private static String safe(String actor) {
        return actor == null || actor.isBlank() ? "-" : actor;
    }
}
