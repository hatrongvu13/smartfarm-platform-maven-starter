package com.htv.smartfarm.order.grpc;

import com.htv.smartfarm.order.domain.OrderEntity;
import com.htv.smartfarm.order.domain.OrderJpaRepository;
import com.htv.smartfarm.order.domain.OrderLineEntity;
import com.htv.smartfarm.order.domain.OrderLineJpaRepository;
import com.htv.smartfarm.order.saga.OrderSagaOrchestrator;
import com.htv.smartfarm.proto.common.v1.Money;
import com.htv.smartfarm.proto.order.v1.*;
import com.htv.smartfarm.security.grpc.GrpcSecurityContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FarmOrderService — the saga owner's gRPC surface. PlaceOrder creates the order aggregate + its
 * lines (idempotent on the request's idempotency_key) and drives the orchestration saga to a
 * terminal state. Supports MULTIPLE order lines: each line reserves + commits its own stock and
 * the order total is the sum of the lines. Tenant and actor come from the verified call context.
 * In this starter every line shares the request's warehouse (carried via {@code batch_id}), since
 * the proto {@code OrderLine} has no per-line warehouse field.
 */
@Service
public class FarmOrderGrpcService extends FarmOrderServiceGrpc.FarmOrderServiceImplBase {

    private final OrderJpaRepository orders;
    private final OrderLineJpaRepository lines;
    private final OrderSagaOrchestrator saga;

    public FarmOrderGrpcService(OrderJpaRepository orders, OrderLineJpaRepository lines, OrderSagaOrchestrator saga) {
        this.orders = orders;
        this.lines = lines;
        this.saga = saga;
    }

    private static String tenant() {
        String t = GrpcSecurityContext.TENANT.get();
        if (t == null || t.isBlank()) throw Status.UNAUTHENTICATED.asRuntimeException();
        return t;
    }

    private static String actor() {
        return GrpcSecurityContext.SUBJECT.get();
    }

    @Override
    @Transactional
    public void placeOrder(PlaceOrderRequest req, StreamObserver<OrderResponse> out) {
        try {
            String tenant = tenant();
            if (!req.hasContext() || req.getContext().getIdempotencyKey().isBlank())
                throw new IllegalArgumentException("idempotency_key required");
            if (req.getFarmId().isBlank()) throw new IllegalArgumentException("farm_id required");
            if (req.getLinesCount() < 1)
                throw new IllegalArgumentException("at least one order line required");

            String key = req.getContext().getIdempotencyKey();
            var existing = orders.findByTenantIdAndIdempotencyKey(tenant, key).orElse(null);
            if (existing != null) {
                out.onNext(OrderResponse.newBuilder()
                        .setOrder(toProto(existing, lines.findByOrderIdOrderByLineNo(existing.getId()))).build());
                out.onCompleted();
                return;
            }

            String defaultWarehouse = req.getBatchId().isBlank() ? "default" : req.getBatchId();
            String currency = null;
            long totalMinor = 0;
            String orderId = UUID.randomUUID().toString();
            // Validate + compute per-line totals first (all-or-nothing before persisting).
            var lineEntities = new java.util.ArrayList<OrderLineEntity>();
            for (int i = 0; i < req.getLinesCount(); i++) {
                OrderLine line = req.getLines(i);
                if (line.getItemId().isBlank() || !line.hasQuantity() || !line.hasUnitPrice())
                    throw new IllegalArgumentException("line " + i + " requires item_id, quantity and unit_price");
                var qty = new BigDecimal(line.getQuantity().getDecimalValue());
                if (qty.signum() <= 0) throw new IllegalArgumentException("line " + i + " quantity must be positive");
                long unitMinor = line.getUnitPrice().getMinorUnits();
                if (unitMinor <= 0) throw new IllegalArgumentException("line " + i + " unit_price must be positive");
                String cur = line.getUnitPrice().getCurrencyCode();
                if (currency == null) currency = cur;
                else if (!currency.equals(cur)) throw new IllegalArgumentException("all lines must use the same currency");
                // Per-line warehouse; falls back to the request-level batch_id for older/single-warehouse callers.
                String lineWarehouse = line.getWarehouseId().isBlank() ? defaultWarehouse : line.getWarehouseId();
                long lineTotal = qty.multiply(BigDecimal.valueOf(unitMinor)).setScale(0, RoundingMode.HALF_UP).longValueExact();
                totalMinor += lineTotal;
                lineEntities.add(new OrderLineEntity(UUID.randomUUID().toString(), orderId, i,
                        line.getItemId(), lineWarehouse, line.getQuantity().getDecimalValue(),
                        line.getQuantity().getUnit(), unitMinor, lineTotal));
            }

            OrderLine first = req.getLines(0);
            String headerWarehouse = first.getWarehouseId().isBlank() ? defaultWarehouse : first.getWarehouseId();
            OrderEntity order = new OrderEntity(orderId, tenant, req.getFarmId(),
                    emptyToNull(req.getBatchId()), first.getItemId(), headerWarehouse,
                    first.getQuantity().getDecimalValue(), first.getQuantity().getUnit(),
                    currency, totalMinor, "ORDER_STATUS_CREATED",
                    System.currentTimeMillis(), key);
            orders.save(order);
            lines.saveAll(lineEntities);

            OrderEntity finalState = saga.run(order, actor());
            out.onNext(OrderResponse.newBuilder().setOrder(toProto(finalState, lineEntities)).build());
            out.onCompleted();
        } catch (IllegalArgumentException e) {
            out.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (io.grpc.StatusRuntimeException e) {
            out.onError(e);
        } catch (RuntimeException e) {
            out.onError(Status.INTERNAL.withDescription("place order failed").asRuntimeException());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void getOrder(GetOrderRequest req, StreamObserver<OrderResponse> out) {
        try {
            var order = orders.findByTenantIdAndId(tenant(), req.getOrderId()).orElse(null);
            if (order == null) {
                out.onError(Status.NOT_FOUND.withDescription("order not found").asRuntimeException());
                return;
            }
            out.onNext(OrderResponse.newBuilder()
                    .setOrder(toProto(order, lines.findByOrderIdOrderByLineNo(order.getId()))).build());
            out.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            out.onError(e);
        } catch (RuntimeException e) {
            out.onError(Status.INTERNAL.withDescription("get order failed").asRuntimeException());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void listOrders(ListOrdersRequest req, StreamObserver<ListOrdersResponse> out) {
        try {
            if (req.getFarmId().isBlank()) throw new IllegalArgumentException("farm_id required");
            int limit = req.hasPage() && req.getPage().getPageSize() > 0 ? req.getPage().getPageSize() : 50;
            String statusFilter = req.getStatus() == OrderStatus.ORDER_STATUS_UNSPECIFIED ? null : req.getStatus().name();
            var list = orders.list(tenant(), req.getFarmId(), statusFilter,
                    org.springframework.data.domain.PageRequest.of(0, Math.max(1, Math.min(limit, 200))));
            var b = ListOrdersResponse.newBuilder();
            for (OrderEntity o : list) b.addOrders(toProto(o, lines.findByOrderIdOrderByLineNo(o.getId())));
            out.onNext(b.build());
            out.onCompleted();
        } catch (IllegalArgumentException e) {
            out.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (io.grpc.StatusRuntimeException e) {
            out.onError(e);
        } catch (RuntimeException e) {
            out.onError(Status.INTERNAL.withDescription("list orders failed").asRuntimeException());
        }
    }

    @Override
    public void cancelOrder(CancelOrderRequest req, StreamObserver<OrderResponse> out) {
        try {
            String tenant = tenant();
            if (req.getOrderId().isBlank()) throw new IllegalArgumentException("order_id required");
            var order = orders.findByTenantIdAndId(tenant, req.getOrderId()).orElse(null);
            if (order == null) {
                out.onError(Status.NOT_FOUND.withDescription("order not found").asRuntimeException());
                return;
            }
            var updated = saga.cancel(order, actor(), req.getReason());
            out.onNext(OrderResponse.newBuilder()
                    .setOrder(toProto(updated, lines.findByOrderIdOrderByLineNo(updated.getId()))).build());
            out.onCompleted();
        } catch (IllegalArgumentException e) {
            out.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (IllegalStateException e) {
            out.onError(Status.FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException());
        } catch (io.grpc.StatusRuntimeException e) {
            out.onError(e);
        } catch (RuntimeException e) {
            out.onError(Status.INTERNAL.withDescription("cancel order failed").asRuntimeException());
        }
    }

    private static FarmOrder toProto(OrderEntity o) {
        return toProto(o, java.util.List.of());
    }

    /**
     * Full projection including per-line detail (item, quantity, unit price, and the line's own
     * warehouse). Read paths pass the persisted lines so clients (REST + GraphQL) see the real
     * multi-line / warehouse-per-line shape; write paths that lack the loaded lines fall back to
     * the header-only overload.
     */
    private static FarmOrder toProto(OrderEntity o, java.util.List<OrderLineEntity> lineRows) {
        var b = FarmOrder.newBuilder()
                .setOrderId(o.getId())
                .setFarmId(o.getFarmId())
                .setStatus(status(o.getStatus()))
                .setTotal(Money.newBuilder().setCurrencyCode(o.getCurrencyCode()).setMinorUnits(o.getTotalMinor()));
        if (o.getBatchId() != null) b.setBatchId(o.getBatchId());
        if (o.getFailureReason() != null) b.setFailureReason(o.getFailureReason());
        for (OrderLineEntity ln : lineRows) {
            b.addLines(OrderLine.newBuilder()
                    .setItemId(ln.getItemId())
                    .setQuantity(com.htv.smartfarm.proto.common.v1.Quantity.newBuilder()
                            .setDecimalValue(ln.getQuantity()).setUnit(ln.getQuantityUnit()))
                    .setUnitPrice(Money.newBuilder()
                            .setCurrencyCode(o.getCurrencyCode()).setMinorUnits(ln.getUnitPriceMinor()))
                    .setWarehouseId(ln.getWarehouseId()));
        }
        return b.build();
    }

    private static OrderStatus status(String s) {
        try {
            return OrderStatus.valueOf(s);
        } catch (IllegalArgumentException ex) {
            return OrderStatus.ORDER_STATUS_UNSPECIFIED;
        }
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
