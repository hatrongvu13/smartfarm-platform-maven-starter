package com.htv.smartfarm.inventory.domain.movement;

public enum MovementKind {
    RECEIPT, ISSUE, ADJUSTMENT, RESERVATION_COMMIT;

    public static MovementKind parse(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("movement kind is required");
        return MovementKind.valueOf(value.trim().toUpperCase());
    }
}
