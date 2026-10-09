package com.htv.smartfarm.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OrderDomainStatusTest {
    @Test
    void shouldParseStoredAndShortNames() {
        assertThat(OrderDomainStatus.fromStored("ORDER_STATUS_DRAFT")).isEqualTo(OrderDomainStatus.DRAFT);
        assertThat(OrderDomainStatus.fromStored("COMPLETED")).isEqualTo(OrderDomainStatus.COMPLETED);
    }

    @Test
    void draftShouldAllowSubmitAndCancelOnly() {
        assertThat(OrderDomainStatus.DRAFT.allowedNext()).containsExactlyInAnyOrder(
                OrderDomainStatus.CREATED, OrderDomainStatus.CANCELLED);
        assertThat(OrderDomainStatus.DRAFT.editable()).isTrue();
    }

    @Test
    void terminalStatesShouldNotAllowTransitions() {
        assertThat(OrderDomainStatus.COMPLETED.allowedNext()).isEmpty();
        assertThat(OrderDomainStatus.CANCELLED.allowedNext()).isEmpty();
    }

    @Test
    void shouldRejectBlankStatus() {
        assertThatThrownBy(() -> OrderDomainStatus.fromStored(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("order status must not be blank");
    }
}
