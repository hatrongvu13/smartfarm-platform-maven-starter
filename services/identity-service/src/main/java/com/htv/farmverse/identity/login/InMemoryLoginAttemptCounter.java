package com.htv.farmverse.identity.login;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bộ đếm trong bộ nhớ của một instance. Chạy nhiều instance thì mỗi instance đếm riêng, nên giới hạn thực tế
 * nhân lên theo số instance. Chỉ dùng cho test trên H2; mặc định là {@link JdbcLoginAttemptCounter}.
 */
public final class InMemoryLoginAttemptCounter implements LoginAttemptCounter {

    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public InMemoryLoginAttemptCounter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Attempt hit(String key, Duration window) {
        long now = clock.millis();
        if (windows.size() > MAX_TRACKED_KEYS) {
            windows.values().removeIf(w -> w.resetAt() <= now);
        }
        Window current = windows.compute(key, (k, w) -> w == null || w.resetAt() <= now
                ? new Window(now + window.toMillis(), 1)
                : new Window(w.resetAt(), w.count() + 1));
        return new Attempt(current.count(), Math.max(1, (current.resetAt() - now + 999) / 1000));
    }

    private record Window(long resetAt, int count) {
    }
}
