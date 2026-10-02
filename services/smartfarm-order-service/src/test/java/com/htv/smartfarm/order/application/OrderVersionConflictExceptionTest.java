package com.htv.smartfarm.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class OrderVersionConflictExceptionTest {
    @Test void shouldExposeExpectedAndActualVersions() {
        var exception = new OrderVersionConflictException(2L, 4L);
        assertThat(exception.expectedVersion()).isEqualTo(2L);
        assertThat(exception.actualVersion()).isEqualTo(4L);
        assertThat(exception.getMessage()).isEqualTo("order version conflict: expected=2, actual=4");
    }
}
