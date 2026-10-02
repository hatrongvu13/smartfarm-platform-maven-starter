package com.htv.smartfarm.order.saga;

import com.htv.smartfarm.order.domain.OrderEntity;
import com.htv.smartfarm.order.domain.OrderJpaRepository;
import com.htv.smartfarm.order.domain.OrderLineEntity;
import com.htv.smartfarm.order.domain.OrderLineJpaRepository;
import com.htv.smartfarm.order.outbox.OrderOutboxEntity;
import com.htv.smartfarm.order.outbox.OrderOutboxJpaRepository;
import com.htv.smartfarm.security.grpc.BearerCallCredentials;
import com.htv.smartfarm.order.grpc.ServiceTokenClient;
import com.htv.smartfarm.proto.common.v1.Money;
import com.htv.smartfarm.proto.common.v1.Quantity;
import com.htv.smartfarm.proto.common.v1.RequestContext;
import com.htv.smartfarm.proto.finance.v1.*;
import com.htv.smartfarm.proto.inventory.v1.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestration-based Saga for placing a MULTI-LINE farm order.
 *
 * <p>Forward path:
 * <ol>
 *   <li>ReserveStock for EACH line (inventory) — reservation id stored per line -> STOCK_RESERVED</li>
 *   <li>RecordExpense once for the order total (finance) -> FINANCE_POSTED</li>
 *   <li>CommitReservation for EACH reserved line (inventory) -> COMPLETED</li>
 * </ol>
 *
 * <p>On any failure, compensation runs in reverse for whatever succeeded: reverse the expense
 * (if posted) and release EVERY reservation that was held (per line), then mark FAILED. Each
 * downstream call carries a per-service token scoped to that audience + the human actor_id for
 * audit; step idempotency keys are derived from the order id (+ line no) so retries are safe.
 */
@Service
public class OrderSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaOrchestrator.class);
    private static final Logger AUDIT = LoggerFactory.getLogger("smartfarm.audit.order");

    private static final String INVENTORY_AUDIENCE = "smartfarm-inventory";
    private static final String FINANCE_AUDIENCE = "smartfarm-finance";

    private final OrderJpaRepository orders;
    private final OrderLineJpaRepository lines;
    private final OrderOutboxJpaRepository outbox;
    private final com.htv.smartfarm.order.outbox.OrderEventStore eventStore;
    private final InventoryServiceGrpc.InventoryServiceBlockingStub inventory;
    private final FarmFinanceServiceGrpc.FarmFinanceServiceBlockingStub finance;
    private final ServiceTokenClient tokens;

    public OrderSagaOrchestrator(OrderJpaRepository orders,
                                 OrderLineJpaRepository lines,
                                 OrderOutboxJpaRepository outbox,
                                 com.htv.smartfarm.order.outbox.OrderEventStore eventStore,
                                 InventoryServiceGrpc.InventoryServiceBlockingStub inventory,
                                 FarmFinanceServiceGrpc.FarmFinanceServiceBlockingStub finance,
                                 ServiceTokenClient tokens) {
        this.orders = orders;
        this.lines = lines;
        this.outbox = outbox;
        this.eventStore = eventStore;
        this.inventory = inventory;
        this.finance = finance;
        this.tokens = tokens;
    }

    private void saveAndEmit(OrderEntity order) {
        orders.save(order);
        eventStore.append(order, order.getId());
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

    public OrderEntity run(OrderEntity order, String actor) {
        List<OrderLineEntity> orderLines = lines.findByOrderIdOrderByLineNo(order.getId());
        try {
            // --- Step 1: reserve EACH line --------------------------------
            for (OrderLineEntity line : orderLines) {
                var resp = inv(order, actor).reserveStock(ReserveStockRequest.newBuilder()
                        .setContext(ctx(order, actor, "reserve:" + line.getLineNo()))
                        .setOrderId(order.getId())
                        .setItemId(line.getItemId())
                        .setWarehouseId(line.getWarehouseId())
                        .setQuantity(Quantity.newBuilder().setDecimalValue(line.getQuantity()).setUnit(line.getQuantityUnit()))
                        .build());
                line.setReservationId(resp.getReservation().getReservationId());
                lines.save(line);
            }
            // keep the first reservation on the order for read-model/back-compat display
            if (!orderLines.isEmpty()) order.setReservationId(orderLines.get(0).getReservationId());
            order.markStockReserved(actor, System.currentTimeMillis());
            saveAndEmit(order);
            AUDIT.info("order_step ok step=reserve order_id={} tenant={} actor_id={} lines={}",
                    order.getId(), order.getTenantId(), safe(actor), orderLines.size());

            // --- Step 2: post finance expense for the order total ---------
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
            order.markFinancePosted(actor, System.currentTimeMillis());
            saveAndEmit(order);
            AUDIT.info("order_step ok step=expense order_id={} tenant={} actor_id={} txn_id={}",
                    order.getId(), order.getTenantId(), safe(actor), txn.getTransaction().getTransactionId());

            // --- Step 3: commit EACH reservation --------------------------
            for (OrderLineEntity line : orderLines) {
                if (line.getReservationId() == null) continue;
                inv(order, actor).commitReservation(CommitReservationRequest.newBuilder()
                        .setContext(ctx(order, actor, "commit:" + line.getLineNo()))
                        .setReservationId(line.getReservationId())
                        .build());
            }
            order.complete(actor, System.currentTimeMillis());
            saveAndEmit(order);
            AUDIT.info("order_completed order_id={} tenant={} actor_id={}", order.getId(), order.getTenantId(), safe(actor));
            return order;

        } catch (StatusRuntimeException e) {
            log.warn("Saga step failed for order {}: {} {}", order.getId(), e.getStatus().getCode(), e.getStatus().getDescription());
            compensate(order, orderLines, actor, describe(e));
            return order;
        } catch (RuntimeException e) {
            log.error("Unexpected saga error for order {}", order.getId(), e);
            compensate(order, orderLines, actor, "internal error");
            return order;
        }
    }

    public OrderEntity cancel(OrderEntity order, String actor, String reason) {
        String s = order.getStatus();
        if ("ORDER_STATUS_CANCELLED".equals(s)) return order; // idempotent
        if ("ORDER_STATUS_COMPLETED".equals(s))
            throw new IllegalStateException("cannot cancel a completed order");
        List<OrderLineEntity> orderLines = lines.findByOrderIdOrderByLineNo(order.getId());
        boolean anyHeld = order.getExpenseTxnId() != null
                || orderLines.stream().anyMatch(l -> l.getReservationId() != null);
        if (anyHeld) {
            compensate(order, orderLines, actor, reason == null || reason.isBlank() ? "cancelled by user" : reason);
        }
        order.cancel(reason, actor, System.currentTimeMillis());
        saveAndEmit(order);
        AUDIT.info("order_cancelled order_id={} tenant={} actor_id={} reason={}",
                order.getId(), order.getTenantId(), safe(actor), reason == null ? "-" : reason);
        return order;
    }

    /** Reverse the expense (if posted) and release EVERY held reservation, then mark FAILED. */
    private void compensate(OrderEntity order, List<OrderLineEntity> orderLines, String actor, String reason) {
        if (order.getExpenseTxnId() != null) {
            try {
                fin(order, actor).reverseExpense(ReverseExpenseRequest.newBuilder()
                        .setContext(ctx(order, actor, "expense-reversal"))
                        .setOriginalExpenseKey(order.getId() + ":expense")
                        .setReversalKey(order.getId() + ":expense-reversal")
                        .build());
                AUDIT.info("order_compensate step=reverse-expense order_id={} tenant={} actor_id={}",
                        order.getId(), order.getTenantId(), safe(actor));
            } catch (RuntimeException ex) {
                log.error("Compensation (reverse expense) failed for order {}", order.getId(), ex);
            }
        }
        if (!"ORDER_STATUS_COMPLETED".equals(order.getStatus())) {
            for (OrderLineEntity line : orderLines) {
                if (line.getReservationId() == null) continue;
                try {
                    inv(order, actor).releaseReservation(ReleaseReservationRequest.newBuilder()
                            .setContext(ctx(order, actor, "release:" + line.getLineNo()))
                            .setReservationId(line.getReservationId())
                            .setReason(reason)
                            .build());
                    AUDIT.info("order_compensate step=release order_id={} line={} tenant={} actor_id={}",
                            order.getId(), line.getLineNo(), order.getTenantId(), safe(actor));
                } catch (RuntimeException ex) {
                    log.error("Compensation (release reservation) failed for order {} line {}", order.getId(), line.getLineNo(), ex);
                }
            }
        }
        order.fail(reason, actor, System.currentTimeMillis());
        saveAndEmit(order);
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
