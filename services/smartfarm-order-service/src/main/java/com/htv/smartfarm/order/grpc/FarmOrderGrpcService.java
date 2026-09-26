package com.htv.smartfarm.order.grpc;

import com.htv.smartfarm.order.domain.OrderEntity;
import com.htv.smartfarm.order.domain.OrderJpaRepository;
import com.htv.smartfarm.order.saga.OrderSagaOrchestrator;
import com.htv.smartfarm.proto.common.v1.Money;
import com.htv.smartfarm.proto.order.v1.*;
import com.htv.smartfarm.security.grpc.GrpcSecurityContext;

import java.math.BigDecimal;
import java.util.UUID;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FarmOrderService — the saga owner's gRPC surface. PlaceOrder creates the order aggregate
 * (idempotent on the request's idempotency_key) and drives the orchestration saga to a
 * terminal state (COMPLETED or FAILED with compensation applied). Tenant and actor come from
 * the verified call context, never the request body.
 *
 * <p>This starter saga handles a single order line (one item), which is enough to exercise
 * reserve -> post finance -> commit with compensation; multi-line fan-out is a later extension.
 */
@Service
public class FarmOrderGrpcService extends FarmOrderServiceGrpc.FarmOrderServiceImplBase {

    private final OrderJpaRepository orders;
    private final OrderSagaOrchestrator saga;

    public FarmOrderGrpcService(OrderJpaRepository orders, OrderSagaOrchestrator saga) {
        this.orders = orders;
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
    public void placeOrder(PlaceOrderRequest req, StreamObserver<OrderResponse> out) {
        try {
            String tenant = tenant();
            if (!req.hasContext() || req.getContext().getIdempotencyKey().isBlank())
                throw new IllegalArgumentException("idempotency_key required");
            if (req.getFarmId().isBlank()) throw new IllegalArgumentException("farm_id required");
            if (req.getLinesCount() != 1)
                throw new IllegalArgumentException("this saga supports exactly one order line");

            String key = req.getContext().getIdempotencyKey();
            var existing = orders.findByTenantIdAndIdempotencyKey(tenant, key).orElse(null);
            if (existing != null) {
                out.onNext(OrderResponse.newBuilder().setOrder(toProto(existing)).build());
                out.onCompleted();
                return;
            }

            OrderLine line = req.getLines(0);
            if (line.getItemId().isBlank() || !line.hasQuantity() || !line.hasUnitPrice())
                throw new IllegalArgumentException("line requires item_id, quantity and unit_price");
            var qty = new BigDecimal(line.getQuantity().getDecimalValue());
            if (qty.signum() <= 0) throw new IllegalArgumentException("quantity must be positive");
            long unitMinor = line.getUnitPrice().getMinorUnits();
            if (unitMinor <= 0) throw new IllegalArgumentException("unit_price must be positive");
            // total = round(qty * unit_price) in minor units
            long totalMinor = qty.multiply(BigDecimal.valueOf(unitMinor)).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
            String warehouseId = req.getBatchId().isBlank() ? "default" : req.getBatchId();

            OrderEntity order = new OrderEntity(UUID.randomUUID().toString(), tenant, req.getFarmId(),
                    emptyToNull(req.getBatchId()), line.getItemId(), warehouseId,
                    line.getQuantity().getDecimalValue(), line.getQuantity().getUnit(),
                    line.getUnitPrice().getCurrencyCode(), totalMinor, "ORDER_STATUS_CREATED",
                    System.currentTimeMillis(), key);
            orders.save(order);

            OrderEntity finalState = saga.run(order, actor());
            out.onNext(OrderResponse.newBuilder().setOrder(toProto(finalState)).build());
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
            out.onNext(OrderResponse.newBuilder().setOrder(toProto(order)).build());
            out.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            out.onError(e);
        } catch (RuntimeException e) {
            out.onError(Status.INTERNAL.withDescription("get order failed").asRuntimeException());
        }
    }

    private static FarmOrder toProto(OrderEntity o) {
        var b = FarmOrder.newBuilder()
                .setOrderId(o.getId())
                .setFarmId(o.getFarmId())
                .setStatus(status(o.getStatus()))
                .setTotal(Money.newBuilder().setCurrencyCode(o.getCurrencyCode()).setMinorUnits(o.getTotalMinor()));
        if (o.getBatchId() != null) b.setBatchId(o.getBatchId());
        if (o.getFailureReason() != null) b.setFailureReason(o.getFailureReason());
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
