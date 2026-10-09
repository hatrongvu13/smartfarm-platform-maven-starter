package com.htv.smartfarm.inventory.grpc;

import com.htv.smartfarm.inventory.domain.item.ItemEntity;
import com.htv.smartfarm.inventory.domain.lot.LotEntity;
import com.htv.smartfarm.inventory.domain.movement.MovementEntity;
import com.htv.smartfarm.proto.common.v1.Quantity;
import com.htv.smartfarm.proto.inventory.v1.InventoryItem;
import com.htv.smartfarm.proto.inventory.v1.ItemCategory;
import com.htv.smartfarm.proto.inventory.v1.MovementType;
import com.htv.smartfarm.proto.inventory.v1.StockLot;
import com.htv.smartfarm.proto.inventory.v1.StockMovement;

import java.math.BigDecimal;

/**
 * Maps Inventory persistence entities to the public protobuf contract.
 *
 * ItemEntity.reorderThreshold is currently stored as a decimal String, while
 * movement quantities are BigDecimal. Separate overloads keep the conversion
 * explicit and prevent String/BigDecimal compilation mismatches.
 */
final class InventoryProtoMapper {

    private InventoryProtoMapper() {
    }

    static InventoryItem item(ItemEntity value) {
        if (value == null) {
            throw new IllegalArgumentException("item is required");
        }

        return InventoryItem.newBuilder()
                .setItemId(required(value.getId(), "item.id"))
                .setSku(required(value.getSku(), "item.sku"))
                .setName(required(value.getName(), "item.name"))
                .setUnit(required(value.getUnit(), "item.unit"))
                .setCategory(itemCategory(value.getCategory().name()))
                .setReorderThreshold(quantity(
                        value.getReorderThreshold(),
                        value.getUnit(),
                        "item.reorderThreshold"
                ))
                .build();
    }

    static StockLot lot(LotEntity value) {
        if (value == null) {
            throw new IllegalArgumentException("lot is required");
        }

        return StockLot.newBuilder()
                .setLotId(required(value.getId(), "lot.id"))
                .setItemId(required(value.getItemId(), "lot.itemId"))
                .setFarmId(required(value.getFarmId(), "lot.farmId"))
                .setWarehouseId(required(value.getWarehouseId(), "lot.warehouseId"))
                .build();
    }

    static StockMovement movement(
            MovementEntity value,
            LotEntity lot,
            String unit
    ) {
        if (value == null) {
            throw new IllegalArgumentException("movement is required");
        }
        if (lot == null) {
            throw new IllegalArgumentException("movement lot is required");
        }

        StockMovement.Builder builder = StockMovement.newBuilder()
                .setMovementId(required(value.getId(), "movement.id"))
                .setLotId(required(value.getLotId(), "movement.lotId"))
                .setItemId(required(lot.getItemId(), "lot.itemId"))
                .setWarehouseId(required(lot.getWarehouseId(), "lot.warehouseId"))
                .setType(movementType(value.getKind().name()))
                .setQuantity(quantity(
                        value.getQuantity(),
                        unit,
                        "movement.quantity"
                ));

        if (value.getReferenceId() != null) {
            builder.setReferenceId(value.getReferenceId());
        }

        return builder.build();
    }

    private static ItemCategory itemCategory(String value) {
        String normalized = required(value, "item.category").toUpperCase();
        if (!normalized.startsWith("ITEM_CATEGORY_")) {
            normalized = "ITEM_CATEGORY_" + normalized;
        }

        try {
            return ItemCategory.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "unsupported item category: " + value,
                    exception
            );
        }
    }

    private static MovementType movementType(String value) {
        String normalized = required(value, "movement.kind").toUpperCase();
        if (!normalized.startsWith("MOVEMENT_TYPE_")) {
            normalized = "MOVEMENT_TYPE_" + normalized;
        }

        try {
            return MovementType.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "unsupported movement kind: " + value,
                    exception
            );
        }
    }

    /** Maps String-backed decimal values such as ItemEntity.reorderThreshold. */
    private static Quantity quantity(
            String decimalValue,
            String unit,
            String field
    ) {
        String normalized = decimalValue == null || decimalValue.isBlank()
                ? "0"
                : decimalValue.trim();

        BigDecimal decimal;
        try {
            decimal = new BigDecimal(normalized);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    field + " must be a base-10 decimal",
                    exception
            );
        }

        return quantity(decimal, unit, field);
    }

    /** Maps native BigDecimal values such as MovementEntity.quantity. */
    private static Quantity quantity(
            BigDecimal value,
            String unit,
            String field
    ) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }

        return Quantity.newBuilder()
                .setDecimalValue(value.toPlainString())
                .setUnit(required(unit, field + ".unit"))
                .build();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
