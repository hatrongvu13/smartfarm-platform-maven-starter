package com.htv.smartfarm.order.outbox.administration;

import java.util.List;
import com.htv.smartfarm.order.outbox.OrderOutboxStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/order-outbox")
@PreAuthorize("hasAuthority('SCOPE_orders:outbox:admin')")
public class OrderOutboxAdministrationController {
    private final OrderOutboxAdministrationService administration;

    public OrderOutboxAdministrationController(OrderOutboxAdministrationService administration) {
        this.administration = administration;
    }

    @GetMapping("/{eventId}")
    public OrderOutboxInspection inspect(@RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable String eventId) {
        return administration.inspect(tenantId, eventId);
    }

    @GetMapping
    public List<OrderOutboxInspection> list(@RequestHeader("X-Tenant-Id") String tenantId,
            @RequestParam(defaultValue = "DEAD") OrderOutboxStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (status != OrderOutboxStatus.FAILED && status != OrderOutboxStatus.DEAD) {
            throw new IllegalArgumentException("only FAILED or DEAD outbox events can be listed");
        }
        return administration.list(tenantId, status, page, size);
    }

    @PostMapping("/{eventId}/retry")
    public OrderOutboxInspection retry(@RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable String eventId) {
        return administration.retry(tenantId, eventId);
    }
}
