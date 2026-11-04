package com.htv.farmverse.gateway;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * I1: đăng xuất thu hồi refresh token ở identity (RFC 7009), rồi mới xóa phiên và chuyển sang {@code /connect/logout}.
 * Đăng nhập thật qua HTTP với identity giả ({@link FakeIdentityServer}), session trong bộ nhớ.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "farmverse.gateway.oidc.client-secret=test-secret",
        "spring.data.redis.password=test",
        "spring.profiles.active=test"
})
@Import(InMemorySessionTestConfiguration.class)
class LogoutRevocationTest {

    static final String SESSION = "FV_SESSION";
    static final FakeIdentityServer IDENTITY = start();

    static FakeIdentityServer start() {
        try {
            return new FakeIdentityServer();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void identity(DynamicPropertyRegistry registry) {
        registry.add("farmverse.gateway.oidc.issuer", IDENTITY::issuer);
    }

    @AfterAll
    static void stop() {
        IDENTITY.close();
    }

    @LocalServerPort
    int port;

    @BeforeEach
    void reset() {
        IDENTITY.revocations.clear();
        IDENTITY.revokeStatus = 200;
    }

    private WebTestClient web() {
        return WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
    }

    /** Đăng nhập trọn vẹn, trả cookie phiên đã đăng nhập. */
    private String login() {
        EntityExchangeResult<byte[]> start = web().get().uri("/oauth2/authorization/farmverse").exchange()
                .expectStatus().is3xxRedirection().expectBody().returnResult();
        Map<String, String> params = UriComponentsBuilder.fromUri(start.getResponseHeaders().getLocation()).build()
                .getQueryParams().toSingleValueMap();
        IDENTITY.nonce = java.net.URLDecoder.decode(params.get("nonce"), StandardCharsets.UTF_8);
        String state = java.net.URLDecoder.decode(params.get("state"), StandardCharsets.UTF_8);
        EntityExchangeResult<byte[]> callback = web().get()
                .uri("/login/oauth2/code/farmverse?code=test-code&state={state}", state)
                .cookie(SESSION, start.getResponseCookies().getFirst(SESSION).getValue())
                .exchange().expectStatus().is3xxRedirection().expectBody().returnResult();
        String session = callback.getResponseCookies().getFirst(SESSION).getValue();
        web().get().uri("/bff/me").cookie(SESSION, session).exchange().expectStatus().isOk();
        return session;
    }

    /** Form đăng xuất của SPA: cookie XSRF-TOKEN + field _csrf. Trả URL chuyển hướng. */
    private URI logout(String session) {
        EntityExchangeResult<byte[]> result = web().post().uri("/logout")
                .cookie(SESSION, session).cookie("XSRF-TOKEN", "csrf-1")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).bodyValue("_csrf=csrf-1")
                .exchange().expectStatus().is3xxRedirection().expectBody().returnResult();
        return result.getResponseHeaders().getLocation();
    }

    @Test
    void logoutRevokesTheRefreshTokenWithClientAuthentication() {
        String session = login();
        String refreshToken = IDENTITY.lastRefreshToken;

        URI next = logout(session);

        assertThat(IDENTITY.revocations).hasSize(1);
        Map<String, String> revoke = IDENTITY.revocations.get(0);
        assertThat(revoke).containsEntry("token", refreshToken).containsEntry("token_type_hint", "refresh_token");
        assertThat(revoke.get("authorization")).isEqualTo("Basic " + Base64.getEncoder()
                .encodeToString((FakeIdentityServer.CLIENT_ID + ":test-secret").getBytes(StandardCharsets.UTF_8)));
        // Vẫn đăng xuất cả phía identity (RP-initiated logout)
        assertThat(next.toString()).startsWith(IDENTITY.issuer() + "/connect/logout");
        web().get().uri("/bff/me").cookie(SESSION, session).exchange().expectStatus().isUnauthorized();
    }

    /** Identity lỗi khi thu hồi: người dùng vẫn được đăng xuất, phiên vẫn bị xóa. */
    @Test
    void logoutStillCompletesWhenRevocationFails() {
        String session = login();
        IDENTITY.revokeStatus = 500;

        URI next = logout(session);

        assertThat(IDENTITY.revocations).hasSize(1);
        assertThat(next.toString()).startsWith(IDENTITY.issuer() + "/connect/logout");
        web().get().uri("/bff/me").cookie(SESSION, session).exchange().expectStatus().isUnauthorized();
    }

    /** Chưa đăng nhập thì không gọi identity. */
    @Test
    void anonymousLogoutDoesNotCallIdentity() {
        web().post().uri("/logout").cookie("XSRF-TOKEN", "csrf-1")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).bodyValue("_csrf=csrf-1")
                .exchange().expectStatus().is3xxRedirection();

        assertThat(IDENTITY.revocations).isEmpty();
    }
}
