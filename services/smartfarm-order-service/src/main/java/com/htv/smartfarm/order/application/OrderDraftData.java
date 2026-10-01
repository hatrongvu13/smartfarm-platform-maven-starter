package com.htv.smartfarm.order.application;

import java.util.List;

public record OrderDraftData(
        String orderId,
        String tenantId,
        String farmId,
        String batchId,
        String currencyCode,
        long totalMinor,
        String status,
        long version,
        long createdAt,
        long updatedAt,
        List<OrderDraftLineData> lines
) {
    public OrderDraftData {
        lines = List.copyOf(lines);
    }
}
