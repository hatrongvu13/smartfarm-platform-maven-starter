package com.htv.smartfarm.gateway.inventory;

import com.htv.smartfarm.gateway.context.GatewayCorrelationContext;
import com.htv.smartfarm.gateway.grpc.GatewayGrpcExceptionMapper;
import com.htv.smartfarm.gateway.identity.GatewayRequestContextFactory;
import com.htv.smartfarm.gateway.identity.ServiceTokenClient;
import com.htv.smartfarm.proto.common.v1.Quantity;
import com.htv.smartfarm.proto.inventory.v1.*;
import com.htv.smartfarm.security.grpc.BearerCallCredentials;
import io.grpc.StatusRuntimeException;
import io.swagger.v3.oas.annotations.Hidden;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@Hidden
@RequestMapping("/api/v1/inventory")
public class InventoryDevSetupController {
    public record CreateItem(String sku, String name, String unit, String reorderThreshold) {
    }

    public record Receive(String itemId, String farmId, String warehouseId, String quantity, String unit) {
    }

    private static final String AUDIENCE = "smartfarm-inventory";
    private final InventoryServiceGrpc.InventoryServiceBlockingStub base;
    private final ServiceTokenClient tokens;
    private final GatewayRequestContextFactory contexts;
    private final GatewayGrpcExceptionMapper errors;
    private final long deadlineMillis;

    public InventoryDevSetupController(InventoryServiceGrpc.InventoryServiceBlockingStub gwInventoryStub,
                                       ServiceTokenClient tokens, GatewayRequestContextFactory contexts,
                                       GatewayGrpcExceptionMapper errors,
                                       @Value("${smartfarm.gateway.grpc.inventory-deadline:5s}") Duration deadline) {
        this.base = gwInventoryStub;
        this.tokens = tokens;
        this.contexts = contexts;
        this.errors = errors;
        this.deadlineMillis = positive(deadline, "inventory deadline");
    }

    @PostMapping("/items")
    @PreAuthorize("hasAuthority('SCOPE_inventory:write')")
    public Mono<Map<String, String>> createItem(@AuthenticationPrincipal Jwt jwt,
                                                @RequestHeader("Idempotency-Key") String key,
                                                @RequestHeader(value = "X-Correlation-Id", required = false) String correlation,
                                                @RequestBody CreateItem body) {
        return call("CreateItem", jwt, () -> {
            var context = contexts.create(jwt, GatewayCorrelationContext.normalize(correlation), key);
            var item = InventoryItem.newBuilder().setSku(body.sku()).setName(body.name()).setUnit(body.unit())
                    .setCategory(ItemCategory.ITEM_CATEGORY_FEED);
            if (body.reorderThreshold() != null && !body.reorderThreshold().isBlank())
                item.setReorderThreshold(Quantity.newBuilder().setDecimalValue(body.reorderThreshold()).setUnit(body.unit()));
            var value = stub(jwt, context.getCorrelationId()).createItem(CreateItemRequest.newBuilder().setContext(context).setItem(item).build()).getItem();
            return Map.of("itemId", value.getItemId(), "sku", value.getSku());
        });
    }

    @PostMapping("/receipts")
    @PreAuthorize("hasAuthority('SCOPE_inventory:write')")
    public Mono<Map<String, String>> receive(@AuthenticationPrincipal Jwt jwt,
                                             @RequestHeader("Idempotency-Key") String key,
                                             @RequestHeader(value = "X-Correlation-Id", required = false) String correlation,
                                             @RequestBody Receive body) {
        return call("ReceiveStock", jwt, () -> {
            var context = contexts.create(jwt, GatewayCorrelationContext.normalize(correlation), key);
            var movement = stub(jwt, context.getCorrelationId()).receiveStock(ReceiveStockRequest.newBuilder()
                    .setContext(context)
                    .setLot(StockLot.newBuilder().setItemId(body.itemId()).setFarmId(body.farmId()).setWarehouseId(body.warehouseId()))
                    .setQuantity(Quantity.newBuilder().setDecimalValue(body.quantity()).setUnit(body.unit()))
                    .setReferenceId("dev-receipt").build()).getMovement();
            return Map.of("movementId", movement.getMovementId(), "lotId", movement.getLotId());
        });
    }

    private InventoryServiceGrpc.InventoryServiceBlockingStub stub(Jwt jwt, String correlation) {
        String tenant = jwt.getClaimAsString("tenant_id");
        String token = tokens.tokenFor(AUDIENCE, tenant, jwt.getSubject());
        return base.withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                .withCallCredentials(new BearerCallCredentials(() -> token, () -> tenant, () -> correlation));
    }

    private <T> Mono<T> call(String operation, Jwt jwt, java.util.concurrent.Callable<T> action) {
        return Mono.fromCallable(() -> {
            try {
                return action.call();
            } catch (StatusRuntimeException e) {
                if (e.getStatus().getCode() == io.grpc.Status.Code.UNAUTHENTICATED)
                    tokens.invalidate(AUDIENCE, jwt.getClaimAsString("tenant_id"));
                throw errors.rest(operation, e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private static long positive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative())
            throw new IllegalArgumentException(field + " must be positive");
        return value.toMillis();
    }
}
