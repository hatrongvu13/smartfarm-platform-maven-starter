package com.htv.smartfarm.order.saga.persistence;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class OrderSagaEntityHardeningTest {
    private static final Instant T = Instant.parse("2026-01-01T00:00:00Z");

    private OrderSagaEntity saga() {
        return new OrderSagaEntity("s", "t", "o", "a", "c", T);
    }

    @Test
    void compensationResetsAttemptsAndKeepsIntent() {
        var s = saga();
        s.claim(T.plusSeconds(1));
        s.retry(T.plusSeconds(9), "DOWN", "retry", T.plusSeconds(2));
        s.beginCompensation(OrderSagaTerminalIntent.CANCELLED, "cancel", T.plusSeconds(600), T.plusSeconds(3));
        assertThat(s.getStatus()).isEqualTo(OrderSagaStatus.COMPENSATING);
        assertThat(s.getAttemptCount()).isZero();
        assertThat(s.getTerminalIntent()).isEqualTo(OrderSagaTerminalIntent.CANCELLED);
        assertThat(s.getClaimedAt()).isNull();
    }

    @Test
    void manualResumeReturnsToCompensationWhenIntentExists() {
        var s = saga();
        s.beginCompensation(OrderSagaTerminalIntent.FAILED, "failed", T.plusSeconds(600), T.plusSeconds(1));
        s.manualReview("DEADLINE", "review", T.plusSeconds(2));
        s.resumeManually(T.plusSeconds(3));
        assertThat(s.getStatus()).isEqualTo(OrderSagaStatus.COMPENSATING);
        assertThat(s.getCompletedAt()).isNull();
    }

    @Test
    void staleRecoveryReleasesClaim() {
        var s = saga();
        s.claim(T.plusSeconds(1));
        s.recover(T.plusSeconds(200));
        assertThat(s.getClaimedAt()).isNull();
        assertThat(s.getLastErrorCode()).isEqualTo("STALE_CLAIM");
    }
}
