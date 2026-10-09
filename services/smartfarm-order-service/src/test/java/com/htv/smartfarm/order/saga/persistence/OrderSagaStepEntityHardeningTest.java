package com.htv.smartfarm.order.saga.persistence;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class OrderSagaStepEntityHardeningTest {
    private static final Instant T = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void compensationRetryKeepsIdempotencyKey() {
        var s = new OrderSagaStepEntity("x", "s", "release:0", 0, OrderSagaStepType.RELEASE_STOCK, "line", "order:release:0", T);
        assertThat(s.getStatus()).isEqualTo(OrderSagaStepStatus.SKIPPED);
        s.activateCompensation(T.plusSeconds(1));
        s.claim(T.plusSeconds(2));
        s.fail(T.plusSeconds(10), "DOWN", "retry", T.plusSeconds(3));
        assertThat(s.getIdempotencyKey()).isEqualTo("order:release:0");
        assertThat(s.getStatus()).isEqualTo(OrderSagaStepStatus.FAILED);
    }

    @Test
    void staleCompensationClaimBecomesFailed() {
        var s = new OrderSagaStepEntity("x", "s", "reverse-finance", 0, OrderSagaStepType.REVERSE_FINANCE, null, "order:reverse-finance", T);
        s.activateCompensation(T.plusSeconds(1));
        s.claim(T.plusSeconds(2));
        s.recover(T.plusSeconds(200));
        assertThat(s.getStatus()).isEqualTo(OrderSagaStepStatus.FAILED);
        assertThat(s.getLastErrorCode()).isEqualTo("STALE_CLAIM");
    }
}
