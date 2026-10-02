package com.htv.smartfarm.order.grpc;

import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import com.htv.smartfarm.order.application.CreateOrderDraftCommand;
import com.htv.smartfarm.order.application.OrderDraftData;
import com.htv.smartfarm.order.application.OrderDraftLineCommand;
import com.htv.smartfarm.order.application.OrderDraftService;
import com.htv.smartfarm.order.application.OrderPage;
import com.htv.smartfarm.order.application.OrderQueryFilter;
import com.htv.smartfarm.order.application.OrderQueryService;
import com.htv.smartfarm.order.application.OrderVersionConflictException;
import com.htv.smartfarm.order.application.UpdateOrderDraftCommand;
import com.htv.smartfarm.order.domain.OrderEntity;
import com.htv.smartfarm.order.domain.OrderJpaRepository;
import com.htv.smartfarm.order.domain.OrderLineEntity;
import com.htv.smartfarm.order.domain.OrderLineJpaRepository;
import com.htv.smartfarm.order.saga.OrderSagaOrchestrator;
import com.htv.smartfarm.order.saga.persistence.OrderSagaTransactionService;
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
 * lines (idempotent on the request's idempotency_key), persists the saga graph, and returns
 * the current order state while the persistent worker advances it. Supports MULTIPLE order lines:
 * the order total is the sum of the lines. Tenant and actor come from the verified call context.
 * In this starter every line shares the request's warehouse (carried via {@code batch_id}), since
 * the proto {@code OrderLine} has no per-line warehouse field.
 */
@Service
public class FarmOrderGrpcService extends FarmOrderServiceGrpc.FarmOrderServiceImplBase {

    private final OrderJpaRepository orders;
    private final OrderLineJpaRepository lines;
    private final OrderSagaOrchestrator saga;
    private final OrderSagaTransactionService persistentSagas;
    private final OrderDraftService drafts;
    private final OrderQueryService queries;

    public FarmOrderGrpcService(
            OrderJpaRepository orders,
            OrderLineJpaRepository lines,
            OrderSagaOrchestrator saga,
            OrderDraftService drafts,
            OrderQueryService queries,
            OrderSagaTransactionService persistentSagas
    ) {
        this.orders = orders;
        this.lines = lines;
        this.saga = saga;
        this.persistentSagas = persistentSagas;
        this.drafts = drafts;
        this.queries = queries;
    }

    private static String tenant() {
        String t = GrpcSecurityContext.TENANT.get();
        if (t == null || t.isBlank()) throw Status.UNAUTHENTICATED.asRuntimeException();
        return t;
    }

    private static String actor() {
        String value = GrpcSecurityContext.SUBJECT.get();
        if (value == null || value.isBlank()) throw Status.UNAUTHENTICATED.asRuntimeException();
        return value;
    }

    private static void requireContext(boolean present) {
        if (!present) throw new IllegalArgumentException("request context is required");
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

            persistentSagas.create(
                    tenant,
                    order.getId(),
                    actor(),
                    req.getContext().getCorrelationId()
            );
            out.onNext(OrderResponse.newBuilder().setOrder(toProto(order, lineEntities)).build());
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
    public void createDraftOrder(
            CreateDraftOrderRequest req,
            StreamObserver<OrderResponse> out
    ) {
        execute(out, "create draft order failed", () -> {
            requireContext(req.hasContext());
            var data = drafts.create(new CreateOrderDraftCommand(
                    tenant(), actor(), req.getContext().getCorrelationId(),
                    required(req.getContext().getIdempotencyKey(), "idempotency_key"),
                    required(req.getFarmId(), "farm_id"), emptyToNull(req.getBatchId()),
                    toDraftLines(req.getLinesList())));
            return OrderResponse.newBuilder().setOrder(toProto(data)).build();
        });
    }

    @Override
    public void updateDraftOrder(
            UpdateDraftOrderRequest req,
            StreamObserver<OrderResponse> out
    ) {
        execute(out, "update draft order failed", () -> {
            requireContext(req.hasContext());
            var data = drafts.update(new UpdateOrderDraftCommand(
                    tenant(), actor(), req.getContext().getCorrelationId(),
                    required(req.getOrderId(), "order_id"), req.getExpectedVersion(),
                    required(req.getFarmId(), "farm_id"), emptyToNull(req.getBatchId()),
                    toDraftLines(req.getLinesList())));
            return OrderResponse.newBuilder().setOrder(toProto(data)).build();
        });
    }

    @Override
    public void deleteDraftOrder(
            DeleteDraftOrderRequest req,
            StreamObserver<Empty> out
    ) {
        execute(out, "delete draft order failed", () -> {
            requireContext(req.hasContext());
            drafts.delete(tenant(), required(req.getOrderId(), "order_id"), req.getExpectedVersion());
            return Empty.getDefaultInstance();
        });
    }

    @Override
    public void submitDraftOrder(
            SubmitDraftOrderRequest req,
            StreamObserver<OrderResponse> out
    ) {
        execute(out, "submit draft order failed", () -> {
            requireContext(req.hasContext());
            var data = drafts.submit(
                    tenant(), required(req.getOrderId(), "order_id"),
                    req.getExpectedVersion(), actor());
            return OrderResponse.newBuilder().setOrder(toProto(data)).build();
        });
    }

    @Override
    public void getOrder(GetOrderRequest req, StreamObserver<OrderResponse> out) {
        execute(out, "get order failed", () -> {
            requireContext(req.hasContext());
            return OrderResponse.newBuilder()
                    .setOrder(toProto(queries.get(tenant(), required(req.getOrderId(), "order_id"))))
                    .build();
        });
    }

    @Override
    public void listOrders(ListOrdersRequest req, StreamObserver<ListOrdersResponse> out) {
        execute(out, "list orders failed", () -> {
            requireContext(req.hasContext());
            int pageSize = req.hasPage() ? req.getPage().getPageSize() : 0;
            String pageToken = req.hasPage() ? req.getPage().getPageToken() : "";
            Long createdFrom = req.hasCreatedRange() && req.getCreatedRange().hasFrom()
                    ? toEpochMillis(req.getCreatedRange().getFrom()) : null;
            Long createdTo = req.hasCreatedRange() && req.getCreatedRange().hasTo()
                    ? toEpochMillis(req.getCreatedRange().getTo()) : null;
            OrderPage page = queries.list(new OrderQueryFilter(
                    tenant(), emptyToNull(req.getFarmId()),
                    req.getStatus() == OrderStatus.ORDER_STATUS_UNSPECIFIED ? null : req.getStatus().name(),
                    emptyToNull(req.getWarehouseId()), createdFrom, createdTo, pageSize, pageToken));
            ListOrdersResponse.Builder response = ListOrdersResponse.newBuilder();
            page.orders().stream().map(FarmOrderGrpcService::toProto).forEach(response::addOrders);
            response.setPage(com.htv.smartfarm.proto.common.v1.PageResponse.newBuilder()
                    .setNextPageToken(page.nextPageToken()));
            return response.build();
        });
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
            if (order.domainStatus() == com.htv.smartfarm.order.domain.OrderDomainStatus.COMPLETED) {
                throw new IllegalStateException("cannot cancel a completed order");
            }
            if (order.domainStatus() == com.htv.smartfarm.order.domain.OrderDomainStatus.CANCELLED) {
                out.onNext(OrderResponse.newBuilder()
                        .setOrder(toProto(order, lines.findByOrderIdOrderByLineNo(order.getId()))).build());
                out.onCompleted();
                return;
            }
            persistentSagas.requestCancellation(
                    tenant,
                    order.getId(),
                    actor(),
                    req.hasContext() ? req.getContext().getCorrelationId() : order.getId(),
                    req.getReason()
            );
            out.onNext(OrderResponse.newBuilder()
                    .setOrder(toProto(order, lines.findByOrderIdOrderByLineNo(order.getId()))).build());
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
                .setTotal(Money.newBuilder().setCurrencyCode(o.getCurrencyCode()).setMinorUnits(o.getTotalMinor()))
                .setVersion(o.getVersion())
                .setCreatedAt(toTimestamp(o.getCreatedAt()))
                .setUpdatedAt(toTimestamp(o.getUpdatedAt()));
        if (o.getBatchId() != null) b.setBatchId(o.getBatchId());
        if (o.getFailureReason() != null) b.setFailureReason(o.getFailureReason());
        for (OrderLineEntity ln : lineRows) {
            b.addLines(OrderLine.newBuilder()
                    .setItemId(ln.getItemId())
                    .setQuantity(com.htv.smartfarm.proto.common.v1.Quantity.newBuilder()
                            .setDecimalValue(ln.getQuantity()).setUnit(ln.getQuantityUnit()))
                    .setUnitPrice(Money.newBuilder()
                            .setCurrencyCode(o.getCurrencyCode()).setMinorUnits(ln.getUnitPriceMinor()))
                    .setWarehouseId(ln.getWarehouseId())
                    .setLineId(ln.getId())
                    .setLineNo(ln.getLineNo())
                    .setLineTotalMinor(ln.getLineTotalMinor())
                    .setVersion(ln.getVersion()));
        }
        return b.build();
    }

    private static FarmOrder toProto(OrderDraftData value) {
        FarmOrder.Builder result = FarmOrder.newBuilder()
                .setOrderId(value.orderId())
                .setFarmId(value.farmId())
                .setStatus(status(value.status()))
                .setTotal(Money.newBuilder()
                        .setCurrencyCode(value.currencyCode())
                        .setMinorUnits(value.totalMinor()))
                .setVersion(value.version())
                .setCreatedAt(toTimestamp(value.createdAt()))
                .setUpdatedAt(toTimestamp(value.updatedAt()));
        if (value.batchId() != null) result.setBatchId(value.batchId());
        value.lines().forEach(line -> result.addLines(OrderLine.newBuilder()
                .setLineId(line.lineId())
                .setLineNo(line.lineNo())
                .setItemId(line.itemId())
                .setWarehouseId(line.warehouseId())
                .setQuantity(com.htv.smartfarm.proto.common.v1.Quantity.newBuilder()
                        .setDecimalValue(line.quantity()).setUnit(line.quantityUnit()))
                .setUnitPrice(Money.newBuilder()
                        .setCurrencyCode(value.currencyCode()).setMinorUnits(line.unitPriceMinor()))
                .setLineTotalMinor(line.lineTotalMinor())
                .setVersion(line.version())));
        return result.build();
    }

    private static java.util.List<OrderDraftLineCommand> toDraftLines(
            java.util.List<OrderLine> values
    ) {
        java.util.ArrayList<OrderDraftLineCommand> result = new java.util.ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            OrderLine line = values.get(index);
            if (!line.hasQuantity() || !line.hasUnitPrice()) {
                throw new IllegalArgumentException("line " + index + " requires quantity and unit_price");
            }
            result.add(new OrderDraftLineCommand(
                    line.getItemId(), line.getWarehouseId(),
                    line.getQuantity().getDecimalValue(), line.getQuantity().getUnit(),
                    line.getUnitPrice().getCurrencyCode(), line.getUnitPrice().getMinorUnits()));
        }
        return java.util.List.copyOf(result);
    }

    private static Timestamp toTimestamp(long epochMillis) {
        return Timestamp.newBuilder()
                .setSeconds(Math.floorDiv(epochMillis, 1000L))
                .setNanos((int) Math.floorMod(epochMillis, 1000L) * 1_000_000)
                .build();
    }

    private static long toEpochMillis(Timestamp value) {
        return Math.addExact(Math.multiplyExact(value.getSeconds(), 1000L), value.getNanos() / 1_000_000L);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " required");
        return value.trim();
    }

    @FunctionalInterface
    private interface GrpcAction<T> { T execute(); }

    private static <T> void execute(StreamObserver<T> out, String internalMessage, GrpcAction<T> action) {
        try {
            T value = action.execute();
            out.onNext(value);
            out.onCompleted();
        } catch (OrderVersionConflictException exception) {
            out.onError(Status.ABORTED.withDescription(exception.getMessage()).asRuntimeException());
        } catch (IllegalArgumentException exception) {
            String message = exception.getMessage();
            Status status = "order not found".equals(message) ? Status.NOT_FOUND : Status.INVALID_ARGUMENT;
            out.onError(status.withDescription(message).asRuntimeException());
        } catch (IllegalStateException exception) {
            out.onError(Status.FAILED_PRECONDITION.withDescription(exception.getMessage()).asRuntimeException());
        } catch (io.grpc.StatusRuntimeException exception) {
            out.onError(exception);
        } catch (RuntimeException exception) {
            out.onError(Status.INTERNAL.withDescription(internalMessage).asRuntimeException());
        }
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
