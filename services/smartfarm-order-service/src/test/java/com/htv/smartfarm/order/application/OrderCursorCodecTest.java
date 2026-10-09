package com.htv.smartfarm.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OrderCursorCodecTest {
    private final OrderCursorCodec codec = new OrderCursorCodec();

    @Test
    void shouldRoundTripStableCursor() {
        String token = codec.encode(1790890000000L, "order-001");
        var cursor = codec.decode(token);
        assertThat(cursor.createdAt()).isEqualTo(1790890000000L);
        assertThat(cursor.orderId()).isEqualTo("order-001");
    }

    @Test
    void blankTokenShouldRepresentFirstPage() {
        assertThat(codec.decode("")).isNull();
        assertThat(codec.decode(null)).isNull();
    }

    @Test
    void shouldRejectMalformedToken() {
        assertThatThrownBy(() -> codec.decode("not-valid-base64***"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("pageToken is invalid");
    }
}
