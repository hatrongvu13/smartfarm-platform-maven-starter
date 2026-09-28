package com.htv.smartfarm.gateway.order;

import com.htv.smartfarm.gateway.identity.ServiceTokenClient;
import com.htv.smartfarm.proto.common.v1.Money;
import com.htv.smartfarm.proto.common.v1.Quantity;
import com.htv.smartfarm.proto.common.v1.RequestContext;
import com.htv.smartfarm.proto.inventory.v1.*;
import com.htv.smartfarm.proto.order.v1.*;
import com.htv.smartfarm.security.grpc.BearerCallCredentials;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.grpc.StatusRuntimeException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * DEV-only REST facade over the order saga + inventory setup, so the Phase 3 saga can be
 * exercised with curl. Every call goes gateway -> gRPC using a per-service token (Phase 2),
 * with the human actor carried in RequestContext.actor_id for traceability. Not active in prod.
 */
@RestController
@Profile("dev & !prod")
@RequestMapping("/api/v1")
public class OrderDevController {

    private static final String ORDER_AUDIENCE = "smartfarm-order";
    private static final String INVENTORY_AUDIENCE = "smartfarm-inventory";

    private final FarmOrderServiceGrpc.FarmOrderServiceBlockingStub orderStub;
    private final InventoryServiceGrpc.InventoryServiceBlockingStub inventoryStub;
    private final ServiceTokenClient serviceTokens;

    public OrderDevController(FarmOrderServiceGrpc.FarmOrderServiceBlockingStub orderStub,
                              InventoryServiceGrpc.InventoryServiceBlockingStub gwInventoryStub,
                              ServiceTokenClient serviceTokens) {
        this.orderStub = orderStub;
        this.inventoryStub = gwInventoryStub;
        this.serviceTokens = serviceTokens;
    }

    // ---- inventory setup (so the saga has stock to reserve) -----------------

    public record CreateItem(String sku, String name, String unit, String reorderThreshold) {
    }

    @PostMapping("/inventory/items")
    @PreAuthorize("hasAuthority('SCOPE_inventory:write')")
    public Mono<Map<String, String>> createItem(@AuthenticationPrincipal Jwt jwt,
                                                 @RequestHeader("Idempotency-Key") String key,
                                                 @RequestBody CreateItem body) {
        return call(jwt, () -> {
            var t = tenant(jwt); var a = jwt.getSubject();
            var item = InventoryItem.newBuilder().setSku(body.sku()).setName(body.name()).setUnit(body.unit())
                    .setCategory(ItemCategory.ITEM_CATEGORY_FEED);
            if (body.reorderThreshold() != null && !body.reorderThreshold().isBlank())
                item.setReorderThreshold(Quantity.newBuilder().setDecimalValue(body.reorderThreshold()).setUnit(body.unit()));
            var resp = invStub(t, a).createItem(CreateItemRequest.newBuilder()
                    .setContext(ctx(t, a, key))
                    .setItem(item)
                    .build());
            return Map.of("itemId", resp.getItem().getItemId(), "sku", resp.getItem().getSku());
        });
    }

    public record Receive(String itemId, String farmId, String warehouseId, String quantity, String unit) {
    }

    @PostMapping("/inventory/receipts")
    @PreAuthorize("hasAuthority('SCOPE_inventory:write')")
    public Mono<Map<String, String>> receive(@AuthenticationPrincipal Jwt jwt,
                                              @RequestHeader("Idempotency-Key") String key,
                                              @RequestBody Receive body) {
        return call(jwt, () -> {
            var t = tenant(jwt); var a = jwt.getSubject();
            var resp = invStub(t, a).receiveStock(ReceiveStockRequest.newBuilder()
                    .setContext(ctx(t, a, key))
                    .setLot(StockLot.newBuilder().setItemId(body.itemId()).setFarmId(body.farmId()).setWarehouseId(body.warehouseId()))
                    .setQuantity(Quantity.newBuilder().setDecimalValue(body.quantity()).setUnit(body.unit()))
                    .setReferenceId("dev-receipt")
                    .build());
            return Map.of("movementId", resp.getMovement().getMovementId(), "lotId", resp.getMovement().getLotId());
        });
    }

    // ---- the saga -----------------------------------------------------------

    // NOTE: unitPriceMinor is boxed (Long) not primitive. A multi-line payload omits the
    // TOP-LEVEL single-line scalars (itemId/quantity/unit/currency/unitPriceMinor), so a
    // primitive `long` there would make Jackson reject the whole body with a message-less
    // 400 (a primitive cannot bind an absent/null JSON value). Boxed = absent -> null, OK.
    public record Line(String itemId, String quantity, String unit, String currency, Long unitPriceMinor, String warehouseId) {
    }

    public record Place(String farmId, String warehouseId, String itemId, String quantity, String unit,
                        String currency, Long unitPriceMinor, java.util.List<Line> lines) {
    }

    @PostMapping("/orders")
    @PreAuthorize("hasAuthority('SCOPE_orders:write')")
    public Mono<Map<String, Object>> place(@AuthenticationPrincipal Jwt jwt,
                                            @RequestHeader("Idempotency-Key") String key,
                                            @RequestBody Place body) {
        return call(jwt, () -> {
            var t = tenant(jwt); var a = jwt.getSubject();
            var req = PlaceOrderRequest.newBuilder()
                    .setContext(ctx(t, a, key))
                    .setFarmId(body.farmId())
                    .setBatchId(body.warehouseId());   // warehouse carried via batch_id in this starter saga
            if (body.lines() != null && !body.lines().isEmpty()) {
                for (Line l : body.lines()) {
                    long priceMinor = l.unitPriceMinor() == null ? 0L : l.unitPriceMinor();
                    var lb = OrderLine.newBuilder()
                            .setItemId(l.itemId())
                            .setQuantity(Quantity.newBuilder().setDecimalValue(l.quantity()).setUnit(l.unit()))
                            .setUnitPrice(Money.newBuilder().setCurrencyCode(l.currency()).setMinorUnits(priceMinor));
                    if (l.warehouseId() != null && !l.warehouseId().isBlank()) lb.setWarehouseId(l.warehouseId());
                    req.addLines(lb);
                }
            } else {
                req.addLines(OrderLine.newBuilder()
                        .setItemId(body.itemId())
                        .setQuantity(Quantity.newBuilder().setDecimalValue(body.quantity()).setUnit(body.unit()))
                        .setUnitPrice(Money.newBuilder().setCurrencyCode(body.currency())
                                .setMinorUnits(body.unitPriceMinor() == null ? 0L : body.unitPriceMinor())));
            }
            var resp = orderStub(t, a).placeOrder(req.build());
            return orderJson(resp.getOrder());
        });
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("hasAuthority('SCOPE_orders:read')")
    public Mono<Map<String, Object>> get(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return call(jwt, () -> {
            var t = tenant(jwt); var a = jwt.getSubject();
            var o = orderStub(t, a).getOrder(GetOrderRequest.newBuilder().setContext(ctx(t, a, "")).setOrderId(id).build()).getOrder();
            return orderJson(o);
        });
    }

    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('SCOPE_orders:read')")
    public Mono<Map<String, Object>> list(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam String farmId,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false, defaultValue = "50") int limit) {
        return call(jwt, () -> {
            var t = tenant(jwt); var a = jwt.getSubject();
            var b = ListOrdersRequest.newBuilder().setContext(ctx(t, a, "")).setFarmId(farmId)
                    .setPage(com.htv.smartfarm.proto.common.v1.PageRequest.newBuilder().setPageSize(limit));
            if (status != null && !status.isBlank()) {
                try {
                    b.setStatus(OrderStatus.valueOf(status.startsWith("ORDER_STATUS_") ? status : "ORDER_STATUS_" + status));
                } catch (IllegalArgumentException ignore) {
                    // unknown status -> no filter
                }
            }
            var resp = orderStub(t, a).listOrders(b.build());
            var items = new java.util.ArrayList<Map<String, Object>>();
            for (FarmOrder o : resp.getOrdersList()) items.add(orderJson(o));
            return Map.of("count", items.size(), "orders", items);
        });
    }

    public record CancelBody(String reason) {
    }

    @PostMapping("/orders/{id}/cancel")
    @PreAuthorize("hasAuthority('SCOPE_orders:write')")
    public Mono<Map<String, Object>> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
                                            @RequestBody(required = false) CancelBody body) {
        return call(jwt, () -> {
            var t = tenant(jwt); var a = jwt.getSubject();
            var o = orderStub(t, a).cancelOrder(CancelOrderRequest.newBuilder().setContext(ctx(t, a, ""))
                    .setOrderId(id).setReason(body == null || body.reason() == null ? "" : body.reason()).build()).getOrder();
            return orderJson(o);
        });
    }

    // ---- helpers ------------------------------------------------------------

    /** Full FE-facing JSON shape of an order, mirroring proto FarmOrder (lines + warehouse per line). */
    private static Map<String, Object> orderJson(FarmOrder o) {
        var m = new java.util.LinkedHashMap<String, Object>();
        m.put("orderId", o.getOrderId());
        m.put("farmId", o.getFarmId());
        m.put("batchId", o.getBatchId());
        m.put("status", o.getStatus().name());
        m.put("totalMinor", o.getTotal().getMinorUnits());
        m.put("currency", o.getTotal().getCurrencyCode());
        if (o.hasCreatedAt())
            m.put("createdAt", java.time.Instant.ofEpochSecond(o.getCreatedAt().getSeconds(), o.getCreatedAt().getNanos()).toString());
        m.put("failureReason", o.getFailureReason());
        var lines = new java.util.ArrayList<Map<String, Object>>();
        for (OrderLine l : o.getLinesList()) {
            var lm = new java.util.LinkedHashMap<String, Object>();
            lm.put("itemId", l.getItemId());
            lm.put("quantity", l.getQuantity().getDecimalValue());
            lm.put("unit", l.getQuantity().getUnit());
            lm.put("unitPriceMinor", l.getUnitPrice().getMinorUnits());
            lm.put("currency", l.getUnitPrice().getCurrencyCode());
            lm.put("warehouseId", l.getWarehouseId());
            lines.add(lm);
        }
        m.put("lines", lines);
        return m;
    }

    private static String tenant(Jwt jwt) {
        return jwt.getClaimAsString("tenant_id");
    }

    private static RequestContext ctx(String tenant, String actor, String key) {
        var b = RequestContext.newBuilder().setTenantId(tenant).setActorId(actor);
        if (key != null && !key.isBlank()) b.setIdempotencyKey(key);
        return b.build();
    }

    private FarmOrderServiceGrpc.FarmOrderServiceBlockingStub orderStub(String tenant, String actor) {
        String token = serviceTokens.tokenFor(ORDER_AUDIENCE, tenant, actor);
        return orderStub.withDeadlineAfter(10, TimeUnit.SECONDS).withCallCredentials(new BearerCallCredentials(() -> token));
    }

    private InventoryServiceGrpc.InventoryServiceBlockingStub invStub(String tenant, String actor) {
        String token = serviceTokens.tokenFor(INVENTORY_AUDIENCE, tenant, actor);
        return inventoryStub.withDeadlineAfter(5, TimeUnit.SECONDS).withCallCredentials(new BearerCallCredentials(() -> token));
    }

    private <T> Mono<T> call(Jwt jwt, java.util.concurrent.Callable<T> action) {
        return Mono.fromCallable(action).subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(StatusRuntimeException.class, e -> {
                    var code = e.getStatus().getCode();
                    if (code == io.grpc.Status.Code.UNAUTHENTICATED) {
                        serviceTokens.invalidate(ORDER_AUDIENCE);
                        serviceTokens.invalidate(INVENTORY_AUDIENCE);
                    }
                    HttpStatus http = switch (code) {
                        case NOT_FOUND -> HttpStatus.NOT_FOUND;
                        case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
                        case FAILED_PRECONDITION, ALREADY_EXISTS -> HttpStatus.CONFLICT;
                        case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
                        case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
                        case DEADLINE_EXCEEDED -> HttpStatus.GATEWAY_TIMEOUT;
                        case UNAVAILABLE, UNIMPLEMENTED -> HttpStatus.BAD_GATEWAY;
                        default -> HttpStatus.BAD_GATEWAY;
                    };
                    return new ResponseStatusException(http, "gRPC " + code + ": " + e.getStatus().getDescription());
                });
    }
}
