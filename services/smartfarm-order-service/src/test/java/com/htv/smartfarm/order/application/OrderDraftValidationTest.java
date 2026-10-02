package com.htv.smartfarm.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderDraftValidationTest {
    @Test void shouldCalculateMultiLineTotalWithHalfUpRounding() {
        var result = OrderDraftValidation.validate(List.of(
                line("1.5", 10001L, "VND"), line("2", 5000L, "VND")));
        assertThat(result.currencyCode()).isEqualTo("VND");
        assertThat(result.lines().get(0).lineTotalMinor()).isEqualTo(15002L);
        assertThat(result.totalMinor()).isEqualTo(25002L);
    }
    @Test void shouldRejectMixedCurrencies() {
        assertThatThrownBy(() -> OrderDraftValidation.validate(List.of(
                line("1", 10000L, "VND"), line("1", 10000L, "USD"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("all order lines must use the same currency");
    }
    @Test void shouldRejectEmptyLines() {
        assertThatThrownBy(() -> OrderDraftValidation.validate(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("at least one order line is required");
    }
    private OrderDraftLineCommand line(String quantity, long price, String currency) {
        return new OrderDraftLineCommand("item-001", "warehouse-001", quantity, "KG", currency, price);
    }
}
