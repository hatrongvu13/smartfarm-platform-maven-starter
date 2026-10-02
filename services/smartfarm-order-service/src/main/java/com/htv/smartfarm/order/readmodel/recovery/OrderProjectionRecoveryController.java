package com.htv.smartfarm.order.readmodel.recovery;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/order-projections")
@PreAuthorize("hasAuthority('SCOPE_orders:admin')")
public class OrderProjectionRecoveryController {
    public record RecoveryRequest(String reason) { }
    private final OrderProjectionManualRecoveryService recovery;
    public OrderProjectionRecoveryController(OrderProjectionManualRecoveryService recovery) {
        this.recovery = recovery;
    }

    @GetMapping("/gaps/{gapId}")
    public OrderProjectionGapDetails inspect(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable String gapId) {
        return recovery.inspect(tenantId, gapId);
    }

    @GetMapping("/gaps")
    public OrderProjectionGapPage list(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return recovery.list(tenantId, status, page, size);
    }

    @PostMapping("/gaps/{gapId}/retry")
    public OrderProjectionGapDetails retry(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable String gapId,
            @RequestBody RecoveryRequest request) {
        return recovery.retry(tenantId, gapId, actor(), request.reason());
    }

    @PostMapping("/gaps/{gapId}/versions/{version}/replay")
    public OrderProjectionGapDetails replay(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable String gapId,
            @PathVariable long version,
            @RequestBody RecoveryRequest request) {
        return recovery.replayExpected(tenantId, gapId, version, actor(), request.reason());
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
