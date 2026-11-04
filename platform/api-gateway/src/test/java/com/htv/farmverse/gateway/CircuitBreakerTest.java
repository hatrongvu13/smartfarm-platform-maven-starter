package com.htv.farmverse.gateway;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockOidcLogin;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/**
 * G3: circuit breaker theo route. Ngưỡng hạ thấp cho test (2 lời gọi, 50%, mở 60 giây).
 * Farm giả có ba chế độ: chậm (quá {@code response-timeout} 300ms), lỗi 500, bình thường. Identity giả luôn trả 200.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "farmverse.gateway.oidc.client-secret=test-secret",
        "spring.data.redis.password=test",
        "spring.profiles.active=test",
        "GATEWAY_RESPONSE_TIMEOUT=300ms",
        "farmverse.gateway.circuit-breaker.minimum-calls=2",
        "farmverse.gateway.circuit-breaker.sliding-window=2",
        "farmverse.gateway.circuit-breaker.wait-in-open-state=60s"
})
@Import(InMemorySessionTestConfiguration.class)
class CircuitBreakerTest {

    enum Mode { SLOW, ERROR_500, OK }

    static volatile Mode farmMode = Mode.OK;
    static final AtomicInteger farmHits = new AtomicInteger();
    static final HttpServer FARM = server(ex -> {
        farmHits.incrementAndGet();
        switch (farmMode) {
            case SLOW -> {
                sleep(2000);
                reply(ex, 200, "{}");
            }
            case ERROR_500 -> reply(ex, 500, "{\"status\":500,\"code\":\"SF-FARM-999\"}");
            case OK -> reply(ex, 200, "[]");
        }
    });
    static final HttpServer IDENTITY = server(ex -> reply(ex, 200, "{\"ok\":true}"));

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("farmverse.gateway.routes.farm-uri", () -> "http://127.0.0.1:" + FARM.getAddress().getPort());
        registry.add("farmverse.gateway.routes.identity-uri", () -> "http://127.0.0.1:" + IDENTITY.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        FARM.stop(0);
        IDENTITY.stop(0);
    }

    @Autowired ApplicationContext context;
    @Autowired ReactiveClientRegistrationRepository registrations;
    @Autowired ReactiveResilience4JCircuitBreakerFactory circuitBreakers;

    WebTestClient user;

    @BeforeEach
    void reset() {
        circuitBreakers.getCircuitBreakerRegistry().getAllCircuitBreakers().forEach(cb -> cb.reset());
        farmHits.set(0);
        farmMode = Mode.OK;
        user = WebTestClient.bindToApplicationContext(context).apply(springSecurity())
                .configureClient().responseTimeout(Duration.ofSeconds(10)).build()
                .mutateWith(mockOidcLogin().clientRegistration(registrations.findByRegistrationId("farmverse").block()));
    }

    WebTestClient.ResponseSpec farm() {
        return user.get().uri("/api/farm/farms").exchange();
    }

    @Test
    void repeatedTimeoutsOpenTheBreakerAndFurtherCallsFailFastWithoutReachingFarm() {
        farmMode = Mode.SLOW;
        farm().expectStatus().isEqualTo(504);
        farm().expectStatus().isEqualTo(504);
        int hitsWhenOpened = farmHits.get();

        long started = System.nanoTime();
        farm().expectStatus().isEqualTo(503)
                .expectHeader().valueEquals("Retry-After", "60")
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody(String.class).value(body -> assertThat(body)
                        .contains("\"code\":\"SF-GATEWAY-503\"")
                        .doesNotContain("127.0.0.1")
                        .doesNotContainIgnoringCase("exception"));
        assertThat(Duration.ofNanos(System.nanoTime() - started)).as("trả ngay, không chờ timeout").isLessThan(Duration.ofMillis(250));
        assertThat(farmHits.get()).as("không gọi farm khi breaker mở").isEqualTo(hitsWhenOpened);
    }

    /** Farm sập không chặn identity: mỗi route một breaker. */
    @Test
    void anOpenFarmBreakerDoesNotAffectIdentity() {
        farmMode = Mode.SLOW;
        farm();
        farm();
        farm().expectStatus().isEqualTo(503);

        user.get().uri("/api/identity/me").exchange().expectStatus().isOk();
    }

    /** Phản hồi 500 của service là câu trả lời, không phải lỗi đường truyền: đi nguyên tới SPA, breaker vẫn đóng. */
    @Test
    void http500FromTheServiceIsPassedThroughAndDoesNotOpenTheBreaker() {
        farmMode = Mode.ERROR_500;
        for (int i = 0; i < 5; i++) {
            farm().expectStatus().isEqualTo(500).expectBody(String.class)
                    .value(body -> assertThat(body).contains("SF-FARM-999"));
        }
        assertThat(farmHits.get()).isEqualTo(5);
        assertThat(circuitBreakers.getCircuitBreakerRegistry().circuitBreaker("farm").getState().name()).isEqualTo("CLOSED");
    }

    // ---- server giả ----

    interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }

    static HttpServer server(Handler handler) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", handler::handle);
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static void reply(HttpExchange ex, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(body);
        } catch (IOException ignored) {
            // gateway đã bỏ kết nối do timeout
        }
    }

    static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
