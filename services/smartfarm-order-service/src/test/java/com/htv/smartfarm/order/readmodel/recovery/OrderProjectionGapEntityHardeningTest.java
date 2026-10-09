package com.htv.smartfarm.order.readmodel.recovery;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class OrderProjectionGapEntityHardeningTest {
    private static final Instant T = Instant.parse("2026-01-01T00:00:00Z");

    private OrderProjectionGapEntity gap() {
        return new OrderProjectionGapEntity("g", "t", "o", 3, 4, 5, 6, T);
    }

    @Test
    void staleClaimPreservesGapRange() {
        var g = gap();
        g.claim("node", T.plusSeconds(1));
        g.recoverStale(T.plusSeconds(200));
        assertThat(g.getStatus()).isEqualTo(OrderProjectionGapStatus.RETRY_WAIT);
        assertThat(g.getMissingFromVersion()).isEqualTo(4);
        assertThat(g.getHighestBufferedVersion()).isEqualTo(6);
    }

    @Test
    void resolvedGapCanReopenForLaterVersions() {
        var g = gap();
        g.resolve(6, T.plusSeconds(1));
        g.extend(6, 9, T.plusSeconds(2));
        assertThat(g.getStatus()).isEqualTo(OrderProjectionGapStatus.OPEN);
        assertThat(g.getMissingFromVersion()).isEqualTo(7);
        assertThat(g.getMissingToVersion()).isEqualTo(8);
    }
}
