package com.htv.farmverse.gateway.config;

import java.time.Duration;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * G3: circuit breaker cho từng route API ({@code identity}, {@code farm}): service phía sau sập kéo dài thì gateway
 * trả 503 ngay, không bắt mọi người chờ {@code connect-timeout}/{@code response-timeout} rồi mới nhận 502/504.
 *
 * <ul>
 *   <li>Chỉ đếm lỗi đường truyền (từ chối kết nối, quá hạn). Phản hồi HTTP của service, kể cả 5xx, đi nguyên tới SPA
 *       và không mở breaker: một endpoint lỗi 500 không được chặn cả service, và ProblemDetail của service
 *       (ví dụ {@code SF-FARM-503}) không bị thay.</li>
 *   <li>Mỗi route một breaker: farm sập không chặn identity.</li>
 *   <li>Time limiter của Resilience4j tắt ({@code spring.cloud.circuitbreaker.resilience4j.disable-time-limiter}):
 *       quá hạn chỉ do {@code httpclient.response-timeout} quyết định, một chỗ duy nhất.</li>
 * </ul>
 * Cấu hình bằng Java (không dùng {@code resilience4j.*} của resilience4j-spring-boot3, vốn viết cho Boot 3).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CircuitBreakerConfiguration.Settings.class)
public class CircuitBreakerConfiguration {

    /**
     * @param failureRate        % lời gọi lỗi trong cửa sổ để mở breaker
     * @param slidingWindow      số lời gọi gần nhất được tính
     * @param minimumCalls       số lời gọi tối thiểu trước khi tính tỉ lệ (tránh mở vì 1-2 lỗi lẻ)
     * @param waitInOpenState    thời gian giữ trạng thái mở rồi mới thử lại (cũng là {@code Retry-After})
     * @param halfOpenCalls      số lời gọi thử ở trạng thái nửa mở
     */
    @ConfigurationProperties("farmverse.gateway.circuit-breaker")
    public record Settings(@DefaultValue("50") float failureRate,
                           @DefaultValue("20") int slidingWindow,
                           @DefaultValue("10") int minimumCalls,
                           @DefaultValue("10s") Duration waitInOpenState,
                           @DefaultValue("3") int halfOpenCalls) {
    }

    @Bean
    Customizer<ReactiveResilience4JCircuitBreakerFactory> farmverseCircuitBreakers(Settings settings) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(settings.slidingWindow())
                .minimumNumberOfCalls(settings.minimumCalls())
                .failureRateThreshold(settings.failureRate())
                .waitDurationInOpenState(settings.waitInOpenState())
                .permittedNumberOfCallsInHalfOpenState(settings.halfOpenCalls())
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id).circuitBreakerConfig(config).build());
    }
}
