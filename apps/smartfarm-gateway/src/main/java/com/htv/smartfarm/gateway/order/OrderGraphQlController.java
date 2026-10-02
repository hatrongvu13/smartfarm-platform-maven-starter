package com.htv.smartfarm.gateway.order;

import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import com.htv.smartfarm.gateway.grpc.GatewayGrpcExceptionMapper;
import com.htv.smartfarm.gateway.context.GatewayCorrelationContext;
import com.htv.smartfarm.gateway.identity.GatewayRequestContextFactory;
import com.htv.smartfarm.gateway.identity.ServiceTokenClient;
import com.htv.smartfarm.proto.common.v1.DateRange;
import com.htv.smartfarm.proto.common.v1.Money;
import com.htv.smartfarm.proto.common.v1.PageRequest;
import com.htv.smartfarm.proto.common.v1.Quantity;
import com.htv.smartfarm.proto.order.v1.*;
import com.htv.smartfarm.security.grpc.BearerCallCredentials;
import io.grpc.StatusRuntimeException;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Controller
public class OrderGraphQlController {
    private static final String AUDIENCE = "smartfarm-order";
    private final FarmOrderServiceGrpc.FarmOrderServiceBlockingStub orders;
    private final ServiceTokenClient tokens;
    private final GatewayRequestContextFactory contexts;
    private final GatewayGrpcExceptionMapper errors;
    private final long deadlineMillis;

    public OrderGraphQlController(FarmOrderServiceGrpc.FarmOrderServiceBlockingStub orders,
            ServiceTokenClient tokens, GatewayRequestContextFactory contexts,
            GatewayGrpcExceptionMapper errors,
            @Value("${smartfarm.gateway.grpc.order-deadline:5s}") Duration deadline) {
        this.orders = orders;
        this.tokens = tokens;
        this.contexts = contexts;
        this.errors = errors;
        this.deadlineMillis = positive(deadline, "order deadline");
    }

    @QueryMapping("orderV2")
    @PreAuthorize("hasAuthority('SCOPE_orders:read')")
    public Mono<Map<String, Object>> order(@Argument String id) {
        return call("GetOrder", (jwt, correlationId) -> {
            var context = contexts.create(jwt, correlationId, null);
            return view(stub(jwt, context.getCorrelationId())
                    .getOrder(GetOrderRequest.newBuilder().setContext(context)
                            .setOrderId(required(id, "id")).build())
                    .getOrder());
        });
    }

    @QueryMapping("ordersV2")
    @PreAuthorize("hasAuthority('SCOPE_orders:read')")
    public Mono<Map<String, Object>> orders(@Argument Map<String, Object> filter,
            @Argument Map<String, Object> page) {
        return call("ListOrders", (jwt, correlationId) -> {
            var context = contexts.create(jwt, correlationId, null);
            ListOrdersRequest.Builder request = ListOrdersRequest.newBuilder()
                    .setContext(context).setPage(page(page));
            if (filter != null) {
                put(filter, "farmId", request::setFarmId);
                put(filter, "warehouseId", request::setWarehouseId);
                Object status = filter.get("status");
                if (status != null && !String.valueOf(status).isBlank()) {
                    request.setStatus(status(String.valueOf(status)));
                }
                Long from = longValue(filter.get("createdFromEpochMs"));
                Long to = longValue(filter.get("createdToEpochMs"));
                if (from != null || to != null) {
                    DateRange.Builder range = DateRange.newBuilder();
                    if (from != null) range.setFrom(timestamp(from));
                    if (to != null) range.setTo(timestamp(to));
                    request.setCreatedRange(range);
                }
            }
            ListOrdersResponse response = stub(jwt, context.getCorrelationId()).listOrders(request.build());
            return Map.of("nodes", response.getOrdersList().stream().map(OrderGraphQlController::view).toList(),
                    "nextPageToken", response.getPage().getNextPageToken());
        });
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('SCOPE_orders:write')")
    public Mono<Map<String, Object>> placeOrder(@Argument Map<String, Object> input) {
        return call("PlaceOrder", (jwt, correlationId) -> {
            String key = required(input, "idempotencyKey");
            var context = contexts.create(jwt, correlationId, key);
            PlaceOrderRequest.Builder request = PlaceOrderRequest.newBuilder()
                    .setContext(context).setFarmId(required(input, "farmId"));
            put(input, "batchId", request::setBatchId);
            lines(input).forEach(request::addLines);
            return view(stub(jwt, context.getCorrelationId()).placeOrder(request.build()).getOrder());
        });
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('SCOPE_orders:write')")
    public Mono<Map<String, Object>> createDraftOrder(@Argument Map<String, Object> input) {
        return call("CreateDraftOrder", (jwt, correlationId) -> {
            var context = contexts.create(jwt, correlationId, required(input, "idempotencyKey"));
            CreateDraftOrderRequest.Builder request = CreateDraftOrderRequest.newBuilder()
                    .setContext(context).setFarmId(required(input, "farmId"));
            put(input, "batchId", request::setBatchId);
            lines(input).forEach(request::addLines);
            return view(stub(jwt, context.getCorrelationId()).createDraftOrder(request.build()).getOrder());
        });
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('SCOPE_orders:write')")
    public Mono<Map<String, Object>> updateDraftOrder(@Argument Map<String, Object> input) {
        return call("UpdateDraftOrder", (jwt, correlationId) -> {
            var context = contexts.create(jwt, correlationId, null);
            UpdateDraftOrderRequest.Builder request = UpdateDraftOrderRequest.newBuilder()
                    .setContext(context).setOrderId(required(input, "orderId"))
                    .setExpectedVersion(requiredLong(input, "expectedVersion"))
                    .setFarmId(required(input, "farmId"));
            put(input, "batchId", request::setBatchId);
            lines(input).forEach(request::addLines);
            return view(stub(jwt, context.getCorrelationId()).updateDraftOrder(request.build()).getOrder());
        });
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('SCOPE_orders:write')")
    public Mono<Map<String, Object>> submitDraftOrder(@Argument Map<String, Object> input) {
        return call("SubmitDraftOrder", (jwt, correlationId) -> {
            var context = contexts.create(jwt, correlationId, null);
            var request = SubmitDraftOrderRequest.newBuilder().setContext(context)
                    .setOrderId(required(input, "orderId"))
                    .setExpectedVersion(requiredLong(input, "expectedVersion")).build();
            return view(stub(jwt, context.getCorrelationId()).submitDraftOrder(request).getOrder());
        });
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('SCOPE_orders:write')")
    public Mono<Boolean> deleteDraftOrder(@Argument Map<String, Object> input) {
        return call("DeleteDraftOrder", (jwt, correlationId) -> {
            var context = contexts.create(jwt, correlationId, null);
            Empty ignored = stub(jwt, context.getCorrelationId()).deleteDraftOrder(
                    DeleteDraftOrderRequest.newBuilder().setContext(context)
                            .setOrderId(required(input, "orderId"))
                            .setExpectedVersion(requiredLong(input, "expectedVersion")).build());
            return ignored != null;
        });
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('SCOPE_orders:write')")
    public Mono<Map<String, Object>> cancelOrder(@Argument Map<String, Object> input) {
        return call("CancelOrder", (jwt, correlationId) -> {
            var context = contexts.create(jwt, correlationId, null);
            CancelOrderRequest.Builder request = CancelOrderRequest.newBuilder().setContext(context)
                    .setOrderId(required(input, "orderId"));
            put(input, "reason", request::setReason);
            return view(stub(jwt, context.getCorrelationId()).cancelOrder(request.build()).getOrder());
        });
    }

    private FarmOrderServiceGrpc.FarmOrderServiceBlockingStub stub(Jwt jwt, String correlationId) {
        String tenant = required(jwt.getClaimAsString("tenant_id"), "tenantId");
        String token = tokens.tokenFor(AUDIENCE, tenant, jwt.getSubject());
        return orders.withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                .withCallCredentials(new BearerCallCredentials(
                        () -> token, () -> tenant, () -> correlationId));
    }

    private <T> Mono<T> call(String operation, CorrelatedAction<T> action) {
        return Mono.deferContextual(reactorContext ->
                ReactiveSecurityContextHolder.getContext()
                        .map(context -> (Jwt) context.getAuthentication().getPrincipal())
                        .map(jwt -> {
                            try { return action.apply(jwt, GatewayCorrelationContext.get(reactorContext)); }
                            catch (StatusRuntimeException exception) {
                                if (exception.getStatus().getCode() == io.grpc.Status.Code.UNAUTHENTICATED) {
                                    tokens.invalidate(AUDIENCE, jwt.getClaimAsString("tenant_id"));
                                }
                                throw errors.graphQl(operation, exception);
                            }
                        }))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @FunctionalInterface
    private interface CorrelatedAction<T> { T apply(Jwt jwt, String correlationId); }

    @SuppressWarnings("unchecked")
    private static List<OrderLine> lines(Map<String, Object> input) {
        Object raw = input == null ? null : input.get("lines");
        if (!(raw instanceof List<?> values) || values.isEmpty()) {
            throw new IllegalArgumentException("lines must not be empty");
        }
        List<OrderLine> result = new ArrayList<>();
        for (Object candidate : values) {
            if (!(candidate instanceof Map<?, ?> source)) throw new IllegalArgumentException("order line is invalid");
            Map<String, Object> line = (Map<String, Object>) source;
            OrderLine.Builder builder = OrderLine.newBuilder()
                    .setItemId(required(line, "itemId"))
                    .setQuantity(Quantity.newBuilder().setDecimalValue(required(line, "quantity"))
                            .setUnit(required(line, "unit")))
                    .setUnitPrice(Money.newBuilder().setCurrencyCode(required(line, "currency"))
                            .setMinorUnits(requiredLong(line, "unitPriceMinor")));
            put(line, "warehouseId", builder::setWarehouseId);
            result.add(builder.build());
        }
        return result;
    }

    private static PageRequest page(Map<String, Object> input) {
        int size = input != null && input.get("size") instanceof Number number ? number.intValue() : 20;
        size = Math.max(1, Math.min(size, 100));
        String token = input == null || input.get("token") == null ? "" : String.valueOf(input.get("token"));
        return PageRequest.newBuilder().setPageSize(size).setPageToken(token).build();
    }

    private static Map<String, Object> view(FarmOrder order) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", order.getOrderId()); result.put("farmId", order.getFarmId());
        result.put("batchId", blank(order.getBatchId())); result.put("status", order.getStatus().name());
        result.put("totalMinor", (double) order.getTotal().getMinorUnits());
        result.put("currency", order.getTotal().getCurrencyCode());
        result.put("version", (double) order.getVersion());
        result.put("createdAt", order.hasCreatedAt() ? instant(order.getCreatedAt()) : null);
        result.put("updatedAt", order.hasUpdatedAt() ? instant(order.getUpdatedAt()) : null);
        result.put("failureReason", blank(order.getFailureReason()));
        result.put("lines", order.getLinesList().stream().map(OrderGraphQlController::lineView).toList());
        return result;
    }

    private static Map<String, Object> lineView(OrderLine line) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("lineId", line.getLineId()); result.put("lineNo", line.getLineNo());
        result.put("itemId", line.getItemId()); result.put("warehouseId", blank(line.getWarehouseId()));
        result.put("quantity", line.getQuantity().getDecimalValue()); result.put("unit", line.getQuantity().getUnit());
        result.put("unitPriceMinor", (double) line.getUnitPrice().getMinorUnits());
        result.put("currency", line.getUnitPrice().getCurrencyCode());
        result.put("lineTotalMinor", (double) line.getLineTotalMinor());
        result.put("version", (double) line.getVersion());
        return result;
    }

    private static long positive(Duration value, String field) { if (value == null || value.isZero() || value.isNegative()) throw new IllegalArgumentException(field + " must be positive"); return value.toMillis(); }
    private static String instant(Timestamp value) { return Instant.ofEpochSecond(value.getSeconds(), value.getNanos()).toString(); }
    private static Timestamp timestamp(long millis) { return Timestamp.newBuilder().setSeconds(Math.floorDiv(millis, 1000)).setNanos((int)Math.floorMod(millis, 1000) * 1_000_000).build(); }
    private static OrderStatus status(String value) { String normalized = value.startsWith("ORDER_STATUS_") ? value : "ORDER_STATUS_" + value; return OrderStatus.valueOf(normalized.toUpperCase()); }
    private static Long longValue(Object value) { return value instanceof Number number ? number.longValue() : null; }
    private static long requiredLong(Map<String, Object> input, String key) { Long value = input == null ? null : longValue(input.get(key)); if (value == null) throw new IllegalArgumentException(key + " is required"); return value; }
    private static String required(Map<String, Object> input, String key) { return required(input == null ? null : input.get(key) == null ? null : String.valueOf(input.get(key)), key); }
    private static String required(String value, String key) { if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " is required"); return value.trim(); }
    private static String blank(String value) { return value == null || value.isBlank() ? null : value; }
    private static void put(Map<String, Object> source, String key, java.util.function.Consumer<String> setter) { if (source != null && source.get(key) != null && !String.valueOf(source.get(key)).isBlank()) setter.accept(String.valueOf(source.get(key))); }
}
