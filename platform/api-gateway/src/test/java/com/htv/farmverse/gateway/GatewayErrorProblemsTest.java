package com.htv.farmverse.gateway;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G2/G3: mọi lỗi gateway tự sinh ra là {@code application/problem+json} có {@code code}, cùng dạng với service
 * phía sau, để SPA xử lý một kiểu duy nhất. Không lộ địa chỉ nội bộ hay tên exception.
 *
 * <p>Route {@code farm-api} trỏ tới server chậm (để có 504), route {@code web} trỏ tới cổng chết (để có 502).
 * Timeout phản hồi đặt qua {@code GATEWAY_RESPONSE_TIMEOUT}, kiểm luôn việc biến được nối ở profile mặc định.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "farmverse.gateway.oidc.client-secret=test-secret",
        "spring.data.redis.password=test",
        "spring.profiles.active=test",
        "WEB_URL=http://127.0.0.1:1",
        "GATEWAY_RESPONSE_TIMEOUT=300ms"
})
@Import(InMemorySessionTestConfiguration.class)
class GatewayErrorProblemsTest {

    /** Trả lời sau 3 giây: chậm hơn timeout 300ms. */
    static final HttpServer SLOW = slowServer();

    static HttpServer slowServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", ex -> {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, body.length);
                try (OutputStream out = ex.getResponseBody()) {
                    out.write(body);
                } catch (IOException ignored) {
                    // gateway đã bỏ kết nối do timeout
                }
            });
            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("farmverse.gateway.routes.farm-uri", () -> "http://127.0.0.1:" + SLOW.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        SLOW.stop(0);
    }

    @LocalServerPort
    int port;

    WebTestClient web() {
        return WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port)
                .responseTimeout(java.time.Duration.ofSeconds(10)).build();
    }

    private static void problem(WebTestClient.ResponseSpec response, int status, String code) {
        response.expectStatus().isEqualTo(status)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody(String.class).value(body -> assertThat(body)
                        .contains("\"status\":" + status)
                        .contains("\"code\":\"" + code + "\"")
                        .doesNotContain("127.0.0.1")
                        .doesNotContainIgnoringCase("exception"));
    }

    @Test
    void unauthenticatedApiAndBffAre401Problem() {
        problem(web().get().uri("/api/farm/farms").exchange(), 401, "SF-AUTH-001");
        problem(web().get().uri("/bff/me").exchange(), 401, "SF-AUTH-001");
    }

    @Test
    void missingCsrfIs403Problem() {
        problem(web().post().uri("/logout").exchange(), 403, "SF-AUTH-002");
    }

    @Test
    void downstreamRefusingConnectionsIs502Problem() {
        problem(web().get().uri("/").exchange(), 502, "SF-GATEWAY-502");
    }

    /**
     * Route API cần đăng nhập, nên dùng client gắn context với phiên giả lập ({@code mockOidcLogin}).
     * Server phía sau chậm 3 giây; timeout 300ms phải cắt và trả 504.
     */
    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.context.ApplicationContext context;

    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository registrations;

    @Test
    void slowDownstreamIs504ProblemWithinTheTimeout() {
        var registration = registrations.findByRegistrationId("farmverse").block();
        WebTestClient mock = WebTestClient.bindToApplicationContext(context)
                .apply(org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity())
                .configureClient().responseTimeout(java.time.Duration.ofSeconds(10)).build();

        long started = System.nanoTime();
        problem(mock.mutateWith(org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers
                        .mockOidcLogin().clientRegistration(registration))
                .get().uri("/api/farm/farms").exchange(), 504, "SF-GATEWAY-504");
        assertThat(java.time.Duration.ofNanos(System.nanoTime() - started)).isLessThan(java.time.Duration.ofSeconds(2));
    }
}
