package com.htv.farmverse.gateway.config;

import java.time.Duration;

import reactor.core.publisher.Mono;

/**
 * Bộ đếm theo cửa sổ thời gian cố định cho {@link LoginRateLimitFilter}. Production: {@link RedisLoginAttemptCounter}
 * (dùng chung giữa các instance). Interface tồn tại để test không cần Redis thay bằng bản trong bộ nhớ.
 */
public interface LoginAttemptCounter {

    /** Tăng bộ đếm của {@code key}; cửa sổ {@code window} bắt đầu ở lần đếm đầu tiên. Trả số lần trong cửa sổ hiện tại. */
    Mono<Long> increment(String key, Duration window);
}
