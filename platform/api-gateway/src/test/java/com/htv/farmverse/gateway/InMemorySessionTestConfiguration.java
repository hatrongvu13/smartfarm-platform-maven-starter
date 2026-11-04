package com.htv.farmverse.gateway;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.session.ReactiveMapSessionRepository;
import org.springframework.session.ReactiveSessionRepository;

/**
 * Kho session trong bộ nhớ cho test, để test gateway không cần Redis đang chạy.
 *
 * <p>Auto-configuration Spring Session Redis của Boot chỉ bật khi chưa có bean {@link ReactiveSessionRepository}
 * ({@code @ConditionalOnMissingBean}), nên khai bean này là đủ để thay Redis. Code chính không đổi:
 * production vẫn dùng Redis.
 *
 * <p>Thứ test này KHÔNG chứng minh: hành vi riêng của Redis (TTL, mất kết nối, serialize). Phần đó cần test
 * tích hợp với Redis thật (Testcontainers), xem {@code docs/architecture/migration-roadmap.md} M2/M3.
 */
@TestConfiguration(proxyBeanMethods = false)
public class InMemorySessionTestConfiguration {

    @Bean
    ReactiveSessionRepository<?> inMemorySessionRepository() {
        return new ReactiveMapSessionRepository(new ConcurrentHashMap<>());
    }
}
