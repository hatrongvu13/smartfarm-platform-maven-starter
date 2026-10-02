package com.htv.smartfarm.messaging.dispatch;

import java.time.Duration;

/**
 * Capped exponential backoff: {@code min(maxDelay, initial * 2^(attempt-1))}. This is the exact
 * formula duplicated in OrderOutboxTransactionService and OrderSagaTransactionService; extracted so
 * every relay computes retry delay the same way. Pure function, no state.
 */
public final class ExponentialBackoff {

    private final Duration initial;
    private final Duration max;

    public ExponentialBackoff(Duration initial, Duration max) {
        if (initial == null || initial.isNegative() || initial.isZero()) {
            throw new IllegalArgumentException("initial must be positive");
        }
        if (max == null || max.compareTo(initial) < 0) {
            throw new IllegalArgumentException("max must be >= initial");
        }
        this.initial = initial;
        this.max = max;
    }

    /** Delay before the given attempt (1-based). Attempt &lt;= 1 returns the initial delay. */
    public Duration delayFor(int attempt) {
        int shift = Math.min(16, Math.max(0, attempt - 1));
        long calculated = initial.toMillis() * (1L << shift);
        return Duration.ofMillis(Math.min(max.toMillis(), calculated));
    }
}
