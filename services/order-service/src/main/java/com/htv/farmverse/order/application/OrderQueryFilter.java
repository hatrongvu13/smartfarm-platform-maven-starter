package com.htv.farmverse.order.application;

public record OrderQueryFilter(
        String tenantId,
        String farmId,
        String status,
        String warehouseId,
        Long createdFrom,
        Long createdTo,
        int pageSize,
        String pageToken
) {
}
