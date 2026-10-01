package com.htv.smartfarm.order.application;

public record OrderDraftLineData(
        String lineId,
        int lineNo,
        String itemId,
        String warehouseId,
        String quantity,
        String quantityUnit,
        long unitPriceMinor,
        long lineTotalMinor,
        long version
) {
}
