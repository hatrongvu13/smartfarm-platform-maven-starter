package com.htv.smartfarm.order.application;

import java.util.List;

public record CreateOrderDraftCommand(
        String tenantId,
        String actorId,
        String correlationId,
        String idempotencyKey,
        String farmId,
        String batchId,
        List<OrderDraftLineCommand> lines
) {
    public CreateOrderDraftCommand {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
