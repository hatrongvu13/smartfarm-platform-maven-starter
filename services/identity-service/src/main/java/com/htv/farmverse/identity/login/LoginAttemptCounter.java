package com.htv.farmverse.identity.login;

import java.time.Duration;

/**
 * Bộ đếm cửa sổ cố định cho {@link LoginRateLimitFilter}: mỗi lần gọi {@link #hit} tăng số lần thử của một khóa
 * (địa chỉ IP) và trả về số lần trong cửa sổ hiện tại.
 *
 * <p>Hai cách lưu (M8, {@code farmverse.identity.rate-limit.store}):
 * <ul>
 *   <li>{@link JdbcLoginAttemptCounter}: PostgreSQL, dùng chung giữa mọi instance. Mặc định.</li>
 *   <li>{@link InMemoryLoginAttemptCounter}: bộ nhớ của một instance. Chỉ cho test trên H2.</li>
 * </ul>
 */
public interface LoginAttemptCounter {

    Attempt hit(String key, Duration window);

    /**
     * @param count             số lần thử trong cửa sổ hiện tại, kể cả lần này
     * @param retryAfterSeconds số giây tới khi cửa sổ kết thúc, ít nhất 1
     */
    record Attempt(int count, long retryAfterSeconds) {
    }
}
