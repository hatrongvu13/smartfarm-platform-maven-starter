package com.htv.smartfarm.inventory.domain.reservation;

public enum ReservationState {
    ACTIVE, RELEASED, COMMITTED, EXPIRED;

    public boolean terminal() {
        return this != ACTIVE;
    }

    public static ReservationState parse(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("reservation status is required");
        return ReservationState.valueOf(value.trim().toUpperCase());
    }
}
