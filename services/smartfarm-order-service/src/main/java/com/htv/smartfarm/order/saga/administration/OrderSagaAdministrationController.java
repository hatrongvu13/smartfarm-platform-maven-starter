package com.htv.smartfarm.order.saga.administration;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/order-sagas")
public class OrderSagaAdministrationController {
    public record RecoveryRequest(String reason) { }
    private final OrderSagaAdministrationService administration;
    public OrderSagaAdministrationController(OrderSagaAdministrationService administration) {
        this.administration = administration;
    }
    @GetMapping("/{sagaId}")
    @PreAuthorize("hasAuthority('SCOPE_orders:saga:read')")
    public OrderSagaInspection inspect(@RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable String sagaId) {
        return administration.inspect(tenantId, sagaId);
    }
    @PostMapping("/{sagaId}/steps/{stepKey}/retry")
    @PreAuthorize("hasAuthority(\'SCOPE_orders:saga:admin\')")
    public OrderSagaInspection retryStep(@RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable String sagaId, @PathVariable String stepKey,
            @RequestBody RecoveryRequest request) {
        return administration.retryStep(tenantId, sagaId, stepKey, actor(), request.reason());
    }
    @PostMapping("/{sagaId}/resume")
    @PreAuthorize("hasAuthority(\'SCOPE_orders:saga:admin\')")
    public OrderSagaInspection resumeSaga(@RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable String sagaId, @RequestBody RecoveryRequest request) {
        return administration.resumeSaga(tenantId, sagaId, actor(), request.reason());
    }
    @PostMapping("/recover-stale")
    @PreAuthorize("hasAuthority(\'SCOPE_orders:saga:admin\')")
    public Map<String, Integer> recoverStale(@RequestHeader("X-Tenant-Id") String tenantId,
            @RequestBody RecoveryRequest request) {
        return Map.of("recovered", administration.recoverStale(tenantId, actor(), request.reason()));
    }
    @PostMapping("/{sagaId}/force-compensate")
    @PreAuthorize("hasAuthority(\'SCOPE_orders:saga:admin\')")
    public OrderSagaInspection forceCompensate(@RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable String sagaId, @RequestBody RecoveryRequest request) {
        return administration.forceCompensate(tenantId, sagaId, actor(), request.reason());
    }

    @PostMapping("/{sagaId}/force-cancel")
    @PreAuthorize("hasAuthority(\'SCOPE_orders:saga:admin\')")
    public OrderSagaInspection forceCancel(@RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable String sagaId, @RequestBody RecoveryRequest request) {
        return administration.forceCancel(tenantId, sagaId, actor(), request.reason());
    }

    @PostMapping("/{sagaId}/force-complete")
    @PreAuthorize("hasAuthority(\'SCOPE_orders:saga:admin\')")
    public OrderSagaInspection forceComplete(@RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable String sagaId, @RequestBody RecoveryRequest request) {
        return administration.forceComplete(tenantId, sagaId, actor(), request.reason());
    }

    @PostMapping("/{sagaId}/mark-manually-resolved")
    @PreAuthorize("hasAuthority(\'SCOPE_orders:saga:admin\')")
    public OrderSagaInspection markManuallyResolved(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable String sagaId, @RequestBody RecoveryRequest request) {
        return administration.markManuallyResolved(tenantId, sagaId, actor(), request.reason());
    }

    private String actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Authenticated operator identity is required");
        }
        return authentication.getName();
    }
}
