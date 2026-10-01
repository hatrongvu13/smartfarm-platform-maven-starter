package com.htv.smartfarm.order.application;

import java.util.List;

public record UpdateOrderDraftCommand(
        String tenantId,
        String actorId,
        String correlationId,
        String orderId,
        long expectedVersion,
        String farmId,
        String batchId,
        List<OrderDraftLineCommand> lines
) {
    public UpdateOrderDraftCommand {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
