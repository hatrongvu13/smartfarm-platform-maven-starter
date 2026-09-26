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

    public record CreateItem(String sku, String name, String unit) {
    }

    @PostMapping("/inventory/items")
    @PreAuthorize("hasAuthority('SCOPE_inventory:write')")
    public Mono<Map<String, String>> createItem(@AuthenticationPrincipal Jwt jwt,
                                                 @RequestHeader("Idempotency-Key") String key,
                                                 @RequestBody CreateItem body) {
        return call(jwt, () -> {
            var t = tenant(jwt); var a = jwt.getSubject();
            var resp = invStub(t, a).createItem(CreateItemRequest.newBuilder()
                    .setContext(ctx(t, a, key))
                    .setItem(InventoryItem.newBuilder().setSku(body.sku()).setName(body.name()).setUnit(body.unit())
                            .setCategory(ItemCategory.ITEM_CATEGORY_FEED))
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

    public record Place(String farmId, String warehouseId, String itemId, String quantity, String unit,
                        String currency, long unitPriceMinor) {
    }

    @PostMapping("/orders")
    @PreAuthorize("hasAuthority('SCOPE_orders:write')")
    public Mono<Map<String, Object>> place(@AuthenticationPrincipal Jwt jwt,
                                            @RequestHeader("Idempotency-Key") String key,
                                            @RequestBody Place body) {
        return call(jwt, () -> {
            var t = tenant(jwt); var a = jwt.getSubject();
            var resp = orderStub(t, a).placeOrder(PlaceOrderRequest.newBuilder()
                    .setContext(ctx(t, a, key))
                    .setFarmId(body.farmId())
                    .setBatchId(body.warehouseId())   // warehouse carried via batch_id in this starter saga
                    .addLines(OrderLine.newBuilder()
                            .setItemId(body.itemId())
                            .setQuantity(Quantity.newBuilder().setDecimalValue(body.quantity()).setUnit(body.unit()))
                            .setUnitPrice(Money.newBuilder().setCurrencyCode(body.currency()).setMinorUnits(body.unitPriceMinor())))
                    .build());
            var o = resp.getOrder();
            return Map.of("orderId", o.getOrderId(), "status", o.getStatus().name(),
                    "failureReason", o.getFailureReason(), "totalMinor", o.getTotal().getMinorUnits());
        });
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("hasAuthority('SCOPE_orders:read')")
    public Mono<Map<String, Object>> get(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return call(jwt, () -> {
            var t = tenant(jwt); var a = jwt.getSubject();
            var o = orderStub(t, a).getOrder(GetOrderRequest.newBuilder().setContext(ctx(t, a, "")).setOrderId(id).build()).getOrder();
            return Map.of("orderId", o.getOrderId(), "status", o.getStatus().name(), "failureReason", o.getFailureReason());
        });
    }

    // ---- helpers ------------------------------------------------------------

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
