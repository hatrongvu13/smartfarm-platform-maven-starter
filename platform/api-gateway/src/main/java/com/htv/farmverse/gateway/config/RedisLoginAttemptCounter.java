package com.htv.farmverse.gateway.config;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

/**
 * Cửa sổ cố định trong Redis: {@code INCR} rồi đặt {@code EXPIRE} ở lần đầu, trong MỘT script Lua (nguyên tử).
 * Hai lệnh rời có thể để lại key không TTL nếu tiến trình chết giữa chừng, và IP đó bị khóa vĩnh viễn.
 *
 * <p>Key nằm dưới {@code farmverse:gateway:} để ACL user {@code gateway} ({@code ~farmverse:gateway:*}) dùng được.
 * Lỗi Redis đi ngược lên thành 503 ({@link RedisUnavailableHandler}).
 */
@Component
public class RedisLoginAttemptCounter implements LoginAttemptCounter {

    static final String PREFIX = "farmverse:gateway:login-limit:";

    private static final RedisScript<Long> INCREMENT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final ReactiveStringRedisTemplate redis;

    public RedisLoginAttemptCounter(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Mono<Long> increment(String key, Duration window) {
        return redis.execute(INCREMENT, List.of(PREFIX + key), List.of(String.valueOf(window.toMillis()))).next();
    }
}
