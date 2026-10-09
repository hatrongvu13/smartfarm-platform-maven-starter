package com.htv.smartfarm.inventory.application.query;

import com.htv.smartfarm.inventory.domain.item.ItemCategoryValue;

import java.math.BigDecimal;

/**
 * Read model for the aggregated stock of one item in one warehouse.
 * <p>
 * The canonical record constructor keeps category as String because protobuf
 * and REST mappers consume the enum name. The explicit overload accepts the
 * actual JPA enum type selected by JPQL, which is required by Hibernate's
 * constructor-expression validation.
 */
public record WarehouseStockView(
        String warehouseId,
        String itemId,
        String sku,
        String itemName,
        String category,
        String unit,
        BigDecimal onHand,
        BigDecimal reserved,
        BigDecimal reorderThreshold,
        long lotCount
) {

    /**
     * Constructor used by JPQL:
     * <p>
     * select new WarehouseStockView(
     * ..., i.category, ..., cast(i.reorderThreshold as big_decimal), count(...)
     * )
     * <p>
     * Hibernate passes ItemCategoryValue for i.category and Long for count().
     */
    public WarehouseStockView(
            String warehouseId,
            String itemId,
            String sku,
            String itemName,
            ItemCategoryValue category,
            String unit,
            BigDecimal onHand,
            BigDecimal reserved,
            BigDecimal reorderThreshold,
            Long lotCount
    ) {
        this(
                required(warehouseId, "warehouseId"),
                required(itemId, "itemId"),
                required(sku, "sku"),
                required(itemName, "itemName"),
                category == null
                        ? ItemCategoryValue.ITEM_CATEGORY_UNSPECIFIED.name()
                        : category.name(),
                required(unit, "unit"),
                zero(onHand),
                zero(reserved),
                zero(reorderThreshold),
                lotCount == null ? 0L : lotCount.longValue()
        );
    }

    public WarehouseStockView {
        warehouseId = required(warehouseId, "warehouseId");
        itemId = required(itemId, "itemId");
        sku = required(sku, "sku");
        itemName = required(itemName, "itemName");
        category = required(category, "category");
        unit = required(unit, "unit");
        onHand = zero(onHand);
        reserved = zero(reserved);
        reorderThreshold = zero(reorderThreshold);
    }

    public BigDecimal available() {
        return onHand.subtract(reserved);
    }

    public boolean lowStock() {
        return reorderThreshold.signum() > 0
                && available().compareTo(reorderThreshold) < 0;
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
