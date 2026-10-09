package com.htv.smartfarm.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.htv.smartfarm.inventory.domain.reservation.ReservationEntity;
import com.htv.smartfarm.inventory.domain.reservation.ReservationState;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * Tests the reservation aggregate lifecycle after status was converted from
 * String to ReservationState.
 */
class ReservationEntityTest {

    private ReservationEntity active() {
        return new ReservationEntity(
                "resv-001",
                "tenant-001",
                "order-001",
                "item-001",
                "lot-001",
                "warehouse-001",
                new BigDecimal("5.000"),
                "ACTIVE",
                "order-001:reserve:0"
        );
    }

    @Test
    void newReservationIsActiveWithGivenQuantityAndKey() {
        ReservationEntity reservation = active();

        assertThat(reservation.getStatus())
                .isEqualTo(ReservationState.ACTIVE);
        assertThat(reservation.getQuantity())
                .isEqualByComparingTo("5.000");
        assertThat(reservation.getIdempotencyKey())
                .isEqualTo("order-001:reserve:0");
        assertThat(reservation.getOrderId())
                .isEqualTo("order-001");
    }

    @Test
    void markReleasedTransitionsStatusButKeepsImmutableFields() {
        ReservationEntity reservation = active();

        reservation.markReleased();

        assertThat(reservation.getStatus())
                .isEqualTo(ReservationState.RELEASED);
        assertThat(reservation.getId())
                .isEqualTo("resv-001");
        assertThat(reservation.getIdempotencyKey())
                .isEqualTo("order-001:reserve:0");
        assertThat(reservation.getQuantity())
                .isEqualByComparingTo("5.000");
    }

    @Test
    void markCommittedTransitionsStatus() {
        ReservationEntity reservation = active();

        reservation.markCommitted();

        assertThat(reservation.getStatus())
                .isEqualTo(ReservationState.COMMITTED);
        assertThat(reservation.getLotId())
                .isEqualTo("lot-001");
        assertThat(reservation.getWarehouseId())
                .isEqualTo("warehouse-001");
    }

    @Test
    void terminalTransitionCannotBeOverwritten() {
        ReservationEntity reservation = active();
        reservation.markReleased();

        assertThatThrownBy(reservation::markCommitted)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RELEASED");

        assertThat(reservation.getStatus())
                .isEqualTo(ReservationState.RELEASED);
    }

    @Test
    void activeReservationCanExpire() {
        ReservationEntity reservation = active();

        reservation.markExpired();

        assertThat(reservation.getStatus())
                .isEqualTo(ReservationState.EXPIRED);
        assertThatThrownBy(reservation::markReleased)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EXPIRED");
    }
}
