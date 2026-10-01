package com.htv.smartfarm.order.application;

public record OrderDraftLineCommand(
        String itemId,
        String warehouseId,
        String quantity,
        String quantityUnit,
        String currencyCode,
        long unitPriceMinor
) {
}
