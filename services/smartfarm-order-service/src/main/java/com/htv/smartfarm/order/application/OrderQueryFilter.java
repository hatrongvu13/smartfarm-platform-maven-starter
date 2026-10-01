package com.htv.smartfarm.order.application;

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
