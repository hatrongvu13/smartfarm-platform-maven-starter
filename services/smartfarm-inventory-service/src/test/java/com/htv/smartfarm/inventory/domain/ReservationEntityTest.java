package com.htv.smartfarm.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the reservation aggregate's lifecycle (ACTIVE -> RELEASED / COMMITTED)
 * and the field invariants the saga depends on (idempotency key + quantity preserved
 * across a status transition). Pure domain — no Spring context, no database.
 */
class ReservationEntityTest {

    private ReservationEntity active() {
        return new ReservationEntity(
                "resv-001", "tenant-001", "order-001", "item-001", "lot-001",
                "warehouse-001", new BigDecimal("5.000"), "ACTIVE", "order-001:reserve:0");
    }

    @Test
    void newReservationIsActiveWithGivenQuantityAndKey() {
        ReservationEntity resv = active();
        assertThat(resv.getStatus()).isEqualTo("ACTIVE");
        assertThat(resv.getQuantity()).isEqualByComparingTo("5.000");
        assertThat(resv.getIdempotencyKey()).isEqualTo("order-001:reserve:0");
        assertThat(resv.getOrderId()).isEqualTo("order-001");
    }

    @Test
    void markReleasedTransitionsStatusButKeepsImmutableFields() {
        ReservationEntity resv = active();
        resv.markReleased();
        assertThat(resv.getStatus()).isEqualTo("RELEASED");
        // Compensation must be able to find the reservation by its stable identity/key.
        assertThat(resv.getId()).isEqualTo("resv-001");
        assertThat(resv.getIdempotencyKey()).isEqualTo("order-001:reserve:0");
        assertThat(resv.getQuantity()).isEqualByComparingTo("5.000");
    }

    @Test
    void markCommittedTransitionsStatus() {
        ReservationEntity resv = active();
        resv.markCommitted();
        assertThat(resv.getStatus()).isEqualTo("COMMITTED");
        assertThat(resv.getLotId()).isEqualTo("lot-001");
        assertThat(resv.getWarehouseId()).isEqualTo("warehouse-001");
    }

    @Test
    void transitionsAreTerminalMostRecentWins() {
        ReservationEntity resv = active();
        resv.markReleased();
        resv.markCommitted();
        // No guard in the aggregate; the last applied transition is reflected. This test
        // documents current behaviour so a future guard change is a conscious decision.
        assertThat(resv.getStatus()).isEqualTo("COMMITTED");
    }
}
