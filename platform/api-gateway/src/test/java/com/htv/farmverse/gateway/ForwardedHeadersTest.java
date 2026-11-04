package com.htv.farmverse.gateway;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5c: gateway đứng sau edge TLS. {@code redirect_uri} phải là URL công khai {@code https://...}, nên gateway đọc
 * {@code X-Forwarded-*} — nhưng CHỈ từ proxy tin cậy ({@code farmverse.gateway.trusted-proxies}). Từ nguồn khác,
 * header đó bị bỏ, để kẻ gọi thẳng không giả được host/scheme.
 *
 * <p>Test gọi từ 127.0.0.1: nhóm {@link BehindTrustedEdge} khai 127.0.0.1 là edge, nhóm {@link Untrusted} thì không.
 */
class ForwardedHeadersTest {

    static final String PUBLIC_HOST = "farm.example.test";
    static final FakeIdentityServer IDENTITY = start();

    static FakeIdentityServer start() {
        try {
            return new FakeIdentityServer();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll
    static void stop() {
        IDENTITY.close();
    }

    static Map<String, String> query(EntityExchangeResult<?> redirect) {
        URI location = redirect.getResponseHeaders().getLocation();
        assertThat(location).isNotNull();
        Map<String, String> params = new LinkedHashMap<>();
        UriComponentsBuilder.fromUri(location).build().getQueryParams().toSingleValueMap()
                .forEach((k, v) -> params.put(k, URLDecoder.decode(v, StandardCharsets.UTF_8)));
        return params;
    }

    static WebTestClient.RequestHeadersSpec<?> viaEdge(WebTestClient.RequestHeadersSpec<?> request) {
        return request.header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", PUBLIC_HOST)
                .header("X-Forwarded-Port", "443");
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
            "farmverse.gateway.oidc.client-secret=test-secret",
            "spring.data.redis.password=test",
            "spring.profiles.active=test",
            "GATEWAY_TRUSTED_PROXIES=127.0.0.1/32"
    })
    @Import(InMemorySessionTestConfiguration.class)
    class BehindTrustedEdge {

        @LocalServerPort
        int port;

        @DynamicPropertySource
        static void identity(DynamicPropertyRegistry registry) {
            registry.add("farmverse.gateway.oidc.issuer", IDENTITY::issuer);
        }

        WebTestClient web() {
            return WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
        }

        @Test
        void redirectUriUsesThePublicHttpsUrl() {
            EntityExchangeResult<byte[]> start = viaEdge(web().get().uri("/oauth2/authorization/farmverse"))
                    .exchange().expectStatus().is3xxRedirection().expectBody().returnResult();

            assertThat(query(start).get("redirect_uri"))
                    .isEqualTo("https://" + PUBLIC_HOST + "/login/oauth2/code/farmverse");
        }

        /** Toàn bộ đăng nhập qua edge: callback so redirect_uri dựng lại từ URL request với bản đã lưu. */
        @Test
        void fullLoginBehindTheEdgeSucceeds() {
            EntityExchangeResult<byte[]> start = viaEdge(web().get().uri("/oauth2/authorization/farmverse"))
                    .exchange().expectBody().returnResult();
            ResponseCookie session = start.getResponseCookies().getFirst("FV_SESSION");
            assertThat(session).isNotNull();
            Map<String, String> params = query(start);
            IDENTITY.nonce = params.get("nonce");

            EntityExchangeResult<byte[]> callback = viaEdge(web().get()
                    .uri("/login/oauth2/code/farmverse?code=edge-code&state={state}", params.get("state"))
                    .cookie("FV_SESSION", session.getValue()))
                    .exchange().expectStatus().is3xxRedirection().expectBody().returnResult();

            assertThat(callback.getResponseHeaders().getLocation()).isNotNull()
                    .satisfies(location -> assertThat(location.toString()).doesNotContain("error"));
            assertThat(IDENTITY.lastTokenRequest)
                    .containsEntry("code", "edge-code")
                    .containsEntry("redirect_uri", "https://" + PUBLIC_HOST + "/login/oauth2/code/farmverse");
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
            "farmverse.gateway.oidc.client-secret=test-secret",
            "spring.data.redis.password=test",
            "spring.profiles.active=test"
    })
    @Import(InMemorySessionTestConfiguration.class)
    class Untrusted {

        @LocalServerPort
        int port;

        @Autowired
        Environment environment;

        @DynamicPropertySource
        static void identity(DynamicPropertyRegistry registry) {
            registry.add("farmverse.gateway.oidc.issuer", IDENTITY::issuer);
        }

        /** Không khai proxy tin cậy (mặc định): header giả bị bỏ, redirect_uri vẫn theo host thật của request. */
        @Test
        void spoofedForwardedHeadersAreIgnored() {
            EntityExchangeResult<byte[]> start = viaEdge(WebTestClient.bindToServer()
                    .baseUrl("http://127.0.0.1:" + port).build()
                    .get().uri("/oauth2/authorization/farmverse"))
                    .header("Forwarded", "proto=https;host=evil.test")
                    .exchange().expectStatus().is3xxRedirection().expectBody().returnResult();

            assertThat(query(start).get("redirect_uri"))
                    .isEqualTo("http://127.0.0.1:" + port + "/login/oauth2/code/farmverse");
        }

        /**
         * Boot tự bật chế độ "native" (Reactor Netty tin X-Forwarded-* của MỌI nguồn) khi phát hiện nền tảng cloud
         * như Kubernetes. Cấu hình phải tắt hẳn để chỉ bộ lọc có danh sách tin cậy của gateway được quyết.
         */
        @Test
        void nativeForwardedHandlingIsExplicitlyOff() {
            assertThat(environment.getProperty("server.forward-headers-strategy")).isEqualToIgnoringCase("none");
        }
    }
}
