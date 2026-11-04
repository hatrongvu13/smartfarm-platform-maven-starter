package com.htv.farmverse.gateway;

import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5: luồng đăng nhập thật qua HTTP (gateway thật + identity giả), kiểm các yêu cầu bảo mật của BFF:
 * <ul>
 *   <li>Authorization request có {@code state}, {@code nonce}, PKCE {@code S256}; lúc đổi code gửi {@code code_verifier}.</li>
 *   <li>Chống session fixation: id phiên trước đăng nhập không còn dùng được sau đăng nhập.</li>
 *   <li>Cookie phiên {@code HttpOnly}, {@code SameSite=Lax}, {@code Path=/}; không chứa token.</li>
 *   <li>{@code state} sai thì không có phiên đăng nhập.</li>
 * </ul>
 * Session ở kho bộ nhớ ({@link InMemorySessionTestConfiguration}); identity giả ở {@link FakeIdentityServer}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "farmverse.gateway.oidc.client-secret=test-secret",
        "spring.data.redis.password=test",
        "spring.profiles.active=test"
})
@Import(InMemorySessionTestConfiguration.class)
class LoginSessionSecurityTest {

    static final String SESSION = "FV_SESSION";
    static final FakeIdentityServer IDENTITY = start();

    @LocalServerPort
    int port;

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

    private WebTestClient web() {
        return WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
    }

    /** Bước 1: SPA chuyển trình duyệt tới đây. Trả về redirect tới identity + cookie phiên trước đăng nhập. */
    private EntityExchangeResult<byte[]> startLogin() {
        return web().get().uri("/oauth2/authorization/farmverse").exchange()
                .expectStatus().is3xxRedirection()
                .expectBody().returnResult();
    }

    private static Map<String, String> query(EntityExchangeResult<?> redirect) {
        URI location = redirect.getResponseHeaders().getLocation();
        assertThat(location).isNotNull();
        // Giá trị trong Location còn mã hóa phần trăm (vd "%3D"); giải mã để gửi lại đúng như trình duyệt.
        Map<String, String> params = new java.util.LinkedHashMap<>();
        UriComponentsBuilder.fromUri(location).build().getQueryParams().toSingleValueMap()
                .forEach((k, v) -> params.put(k, java.net.URLDecoder.decode(v, java.nio.charset.StandardCharsets.UTF_8)));
        return params;
    }

    private static ResponseCookie sessionCookie(EntityExchangeResult<?> result) {
        ResponseCookie cookie = result.getResponseCookies().getFirst(SESSION);
        assertThat(cookie).as("Set-Cookie " + SESSION).isNotNull();
        return cookie;
    }

    @Test
    void authorizationRequestCarriesStateNonceAndPkce() {
        Map<String, String> params = query(startLogin());

        assertThat(params.get("response_type")).isEqualTo("code");
        assertThat(params.get("client_id")).isEqualTo(FakeIdentityServer.CLIENT_ID);
        assertThat(params.get("state")).isNotBlank();
        assertThat(params.get("nonce")).isNotBlank();
        assertThat(params.get("code_challenge")).isNotBlank();
        assertThat(params.get("code_challenge_method")).isEqualTo("S256");
        assertThat(params.get("scope")).contains("openid");
    }

    @Test
    void stateAndNonceAreFreshForEachLoginAttempt() {
        Map<String, String> first = query(startLogin());
        Map<String, String> second = query(startLogin());

        assertThat(second.get("state")).isNotEqualTo(first.get("state"));
        assertThat(second.get("nonce")).isNotEqualTo(first.get("nonce"));
        assertThat(second.get("code_challenge")).isNotEqualTo(first.get("code_challenge"));
    }

    @Test
    void loginRotatesTheSessionIdAndTheOldIdIsUseless() {
        EntityExchangeResult<byte[]> start = startLogin();
        String before = sessionCookie(start).getValue();
        Map<String, String> params = query(start);
        IDENTITY.nonce = params.get("nonce");

        EntityExchangeResult<byte[]> callback = web().get()
                .uri("/login/oauth2/code/farmverse?code=test-code&state={state}", params.get("state"))
                .cookie(SESSION, before)
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectBody().returnResult();

        // Đổi code có PKCE
        assertThat(IDENTITY.lastTokenRequest)
                .containsEntry("grant_type", "authorization_code")
                .containsEntry("code", "test-code")
                .containsKey("code_verifier");
        assertThat(callback.getResponseHeaders().getLocation()).hasPath("/");

        ResponseCookie after = sessionCookie(callback);
        assertThat(after.getValue()).as("id phiên phải đổi sau đăng nhập").isNotEqualTo(before);

        // Phiên mới đã đăng nhập; phiên cũ (kẻ tấn công có thể đã cài sẵn) không có quyền gì
        web().get().uri("/bff/me").cookie(SESSION, after.getValue()).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.userId").isEqualTo(FakeIdentityServer.USER_ID)
                .jsonPath("$.tenantId").isEqualTo(FakeIdentityServer.TENANT_ID);
        web().get().uri("/bff/me").cookie(SESSION, before).exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void sessionCookieIsHttpOnlyLaxRootPathAndOpaque() {
        EntityExchangeResult<byte[]> start = startLogin();
        Map<String, String> params = query(start);
        IDENTITY.nonce = params.get("nonce");
        EntityExchangeResult<byte[]> callback = web().get()
                .uri("/login/oauth2/code/farmverse?code=test-code&state={state}", params.get("state"))
                .cookie(SESSION, sessionCookie(start).getValue())
                .exchange().expectBody().returnResult();

        for (ResponseCookie cookie : new ResponseCookie[] {sessionCookie(start), sessionCookie(callback)}) {
            assertThat(cookie.isHttpOnly()).isTrue();
            assertThat(cookie.getSameSite()).isEqualTo("Lax");
            assertThat(cookie.getPath()).isEqualTo("/");
            // Opaque: không phải JWT, không chứa dấu chấm của token
            assertThat(cookie.getValue()).doesNotContain(".").hasSizeLessThan(100);
        }
        // Không có token nào trong bất kỳ cookie nào trình duyệt nhận
        callback.getResponseCookies().values().stream().flatMap(java.util.List::stream)
                .forEach(c -> assertThat(c.getValue()).doesNotStartWith("eyJ"));
    }

    @Test
    void wrongStateDoesNotLogIn() {
        EntityExchangeResult<byte[]> start = startLogin();
        String before = sessionCookie(start).getValue();
        IDENTITY.nonce = query(start).get("nonce");

        EntityExchangeResult<byte[]> callback = web().get()
                .uri("/login/oauth2/code/farmverse?code=test-code&state=forged")
                .cookie(SESSION, before)
                .exchange()
                .expectBody().returnResult();

        ResponseCookie rotated = callback.getResponseCookies().getFirst(SESSION);
        String session = rotated != null ? rotated.getValue() : before;
        web().get().uri("/bff/me").cookie(SESSION, session).exchange().expectStatus().isUnauthorized();
    }
}
