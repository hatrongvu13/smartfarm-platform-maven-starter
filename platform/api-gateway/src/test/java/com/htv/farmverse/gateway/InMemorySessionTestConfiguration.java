package com.htv.farmverse.gateway;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.htv.farmverse.gateway.config.LoginAttemptCounter;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.session.ReactiveMapSessionRepository;
import org.springframework.session.ReactiveSessionRepository;

import reactor.core.publisher.Mono;

/**
 * Thay các phần dùng Redis bằng bản trong bộ nhớ, để test gateway không cần Redis đang chạy:
 * <ul>
 *   <li>Kho session: auto-configuration Spring Session Redis của Boot chỉ bật khi chưa có bean
 *       {@link ReactiveSessionRepository} ({@code @ConditionalOnMissingBean}), nên khai bean này là đủ.</li>
 *   <li>Bộ đếm đăng nhập theo IP ({@link LoginAttemptCounter}): {@code @Primary} đè bản Redis. Không có cửa sổ thời gian;
 *       profile test đặt giới hạn rất cao để các test đăng nhập dùng chung context không bị chặn.</li>
 * </ul>
 * Code chính không đổi: production vẫn dùng Redis.
 *
 * <p>Thứ test này KHÔNG chứng minh: hành vi riêng của Redis (TTL, mất kết nối, serialize, ACL). Phần đó ở
 * {@code GatewayRedisIT} (Testcontainers).
 */
@TestConfiguration(proxyBeanMethods = false)
public class InMemorySessionTestConfiguration {

    @Bean
    ReactiveSessionRepository<?> inMemorySessionRepository() {
        return new ReactiveMapSessionRepository(new ConcurrentHashMap<>());
    }

    @Bean
    @Primary
    LoginAttemptCounter inMemoryLoginAttemptCounter() {
        ConcurrentHashMap<String, AtomicLong> counts = new ConcurrentHashMap<>();
        return (String key, Duration window) ->
                Mono.fromSupplier(() -> counts.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet());
    }
}
