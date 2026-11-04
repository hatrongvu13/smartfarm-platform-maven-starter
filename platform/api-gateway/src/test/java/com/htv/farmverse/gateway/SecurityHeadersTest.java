package com.htv.farmverse.gateway;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5b: header bảo mật trên trang web (đi qua route {@code web} tới SPA) và trên API.
 *
 * <p>Route {@code web} trỏ tới một server HTML giả, nên test kiểm đúng đường thật: header phải còn sau khi
 * gateway proxy phản hồi của SPA. Không cần Redis: các request ở đây không có cookie phiên.
 */
class SecurityHeadersTest {

    static final String IDENTITY = "http://127.0.0.1:18092";
    static final HttpServer WEB = startWeb();

    static HttpServer startWeb() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", ex -> {
                byte[] body = "<!doctype html><title>FarmVerse</title>".getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "text/html");
                ex.sendResponseHeaders(200, body.length);
                try (OutputStream out = ex.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static void common(DynamicPropertyRegistry registry) {
        registry.add("WEB_URL", () -> "http://127.0.0.1:" + WEB.getAddress().getPort());
        registry.add("farmverse.gateway.oidc.issuer", () -> IDENTITY + "/idp");
    }

    @AfterAll
    static void stop() {
        WEB.stop(0);
    }

    static WebTestClient client(int port) {
        return WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
    }

    /** Cấu hình mặc định (giống prod về CSP, nhưng chưa bật HSTS). */
    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
            "farmverse.gateway.oidc.client-secret=test-secret",
            "spring.data.redis.password=test",
            "spring.profiles.active=test"
    })
    @Import(InMemorySessionTestConfiguration.class)
    class Defaults {

        @LocalServerPort
        int port;

        @DynamicPropertySource
        static void props(DynamicPropertyRegistry registry) {
            common(registry);
        }

        @Test
        void spaPageThroughTheGatewayCarriesStrictCsp() {
            client(port).get().uri("/").exchange()
                    .expectStatus().isOk()
                    .expectHeader().valueEquals("Content-Security-Policy",
                            "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: blob:; "
                                    + "font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; "
                                    + "frame-ancestors 'none'; form-action 'self' " + IDENTITY)
                    .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                    .expectHeader().valueEquals("X-Frame-Options", "DENY")
                    .expectHeader().valueEquals("Referrer-Policy", "strict-origin-when-cross-origin")
                    .expectHeader().valueEquals("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()");
        }

        /** HSTS tắt khi không bật cờ (dev chạy HTTP thuần trên máy). */
        @Test
        void noHstsUnlessEnabled() {
            client(port).get().uri("/").exchange()
                    .expectHeader().doesNotExist("Strict-Transport-Security");
        }

        /** API (kể cả lỗi 401) cũng có header. */
        @Test
        void apiErrorsCarryHeadersToo() {
            client(port).get().uri("/api/farm/farms").exchange()
                    .expectStatus().isUnauthorized()
                    .expectHeader().exists("Content-Security-Policy")
                    .expectHeader().valueEquals("X-Content-Type-Options", "nosniff");
        }
    }

    /** Prod: TLS kết thúc ở edge nên gateway thấy HTTP; HSTS vẫn phải được gửi. */
    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
            "farmverse.gateway.oidc.client-secret=test-secret",
            "spring.data.redis.password=test",
            "spring.profiles.active=test",
            "farmverse.gateway.headers.hsts=true"
    })
    @Import(InMemorySessionTestConfiguration.class)
    class HstsBehindTlsProxy {

        @LocalServerPort
        int port;

        @DynamicPropertySource
        static void props(DynamicPropertyRegistry registry) {
            common(registry);
        }

        @Test
        void hstsIsSentExactlyOnce() {
            client(port).get().uri("/").exchange()
                    .expectHeader().values("Strict-Transport-Security", values ->
                            assertThat(values).containsExactly("max-age=31536000"));
        }
    }

    /** Khóa cấu hình theo profile: prod bật HSTS, CSP mặc định không có 'unsafe-*'; chỉ dev được nới cho Vite. */
    @Test
    void profileConfiguration() {
        assertThat(yaml("application-prod.yml")).containsEntry("farmverse.gateway.headers.hsts", true);
        assertThat(String.valueOf(yaml("application.yml").get("farmverse.gateway.headers.content-security-policy")))
                .doesNotContain("unsafe-");
        assertThat(String.valueOf(yaml("application-dev.yml").get("farmverse.gateway.headers.content-security-policy")))
                .contains("'unsafe-inline'")
                .doesNotContain("unsafe-eval");
    }

    private static java.util.Properties yaml(String file) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource(file));
        return factory.getObject();
    }
}
