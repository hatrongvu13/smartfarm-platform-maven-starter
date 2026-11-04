package com.htv.farmverse.gateway;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.data.redis.ReactiveRedisSessionRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M3: session ở Redis thật (KHÔNG import kho bộ nhớ), Redis trỏ vào cổng chết.
 *
 * <ul>
 *   <li>Cấu hình: TTL phiên, namespace, timeout lệnh/kết nối lấy từ biến môi trường. Test đặt giá trị khác mặc
 *       định để chứng minh biến thật sự được nối, không phải trùng mặc định của Spring.</li>
 *   <li>Fail-closed: Redis không trả lời thì request có phiên bị chặn với 503 ProblemDetail, không bao giờ cho qua
 *       và không lộ chi tiết lỗi.</li>
 * </ul>
 * {@code createSession()} chỉ tạo đối tượng trong bộ nhớ, không gọi Redis, nên chạy được khi không có Redis.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "farmverse.gateway.oidc.client-secret=test-secret",
        "spring.data.redis.password=test",
        "spring.profiles.active=test",
        "REDIS_PORT=1",                    // cổng chết: kết nối bị từ chối ngay
        "GATEWAY_SESSION_TIMEOUT=7m",
        "REDIS_TIMEOUT=1500ms",
        "REDIS_CONNECT_TIMEOUT=750ms"
})
class RedisSessionHardeningTest {

    @LocalServerPort
    int port;

    @Autowired
    ReactiveSessionRepository<? extends Session> sessions;

    @Autowired
    LettuceConnectionFactory redis;

    @Test
    void sessionLivesInRedisWithConfiguredTtlAndNamespace() {
        assertThat(sessions).isInstanceOf(ReactiveRedisSessionRepository.class);
        Session session = sessions.createSession().block();
        assertThat(session).isNotNull();
        assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ofMinutes(7));
        assertThat(ReflectionTestUtils.getField(sessions, "namespace")).isEqualTo("farmverse:gateway:session:");
    }

    @Test
    void redisClientHasCommandAndConnectTimeouts() {
        assertThat(redis.getClientConfiguration().getCommandTimeout()).isEqualTo(Duration.ofMillis(1500));
        assertThat(redis.getClientConfiguration().getClientOptions()).hasValueSatisfying(options ->
                assertThat(options.getSocketOptions().getConnectTimeout()).isEqualTo(Duration.ofMillis(750)));
    }

    @Test
    void redisDownFailsClosedWith503Problem() {
        WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build()
                .get().uri("/bff/me")
                .cookie("FV_SESSION", "00000000-0000-0000-0000-000000000000")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().valueEquals("Retry-After", "5")
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.code").isEqualTo("SF-GATEWAY-503")
                .jsonPath("$.trace").doesNotExist()
                .jsonPath("$.exception").doesNotExist();
    }

    /** Không có cookie phiên thì không cần đọc Redis: vẫn là 401 bình thường, không phải 503. */
    @Test
    void anonymousRequestWithoutSessionCookieIsStill401() {
        WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build()
                .get().uri("/bff/me").exchange()
                .expectStatus().isUnauthorized();
    }
}
