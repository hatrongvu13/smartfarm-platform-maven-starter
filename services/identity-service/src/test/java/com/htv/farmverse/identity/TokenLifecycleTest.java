package com.htv.farmverse.identity;

import java.util.Base64;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vòng đời token của client gateway: xoay vòng refresh token, thu hồi (revoke), đăng xuất RP-initiated.
 * Mỗi test tự đăng nhập để không phụ thuộc test khác.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TokenLifecycleTest {

    static final String REDIRECT = "http://127.0.0.1:8080/login/oauth2/code/farmverse";
    static final String POST_LOGOUT = "http://127.0.0.1:8080/";
    static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
    static final String BASIC = "Basic " + Base64.getEncoder().encodeToString(
            "farmverse-gateway:test-gateway-secret-0123456789abcdef".getBytes());

    @Autowired
    MockMvc mvc;

    @Test
    void refreshTokenRotatesAndTheOldOneStopsWorking() throws Exception {
        String tokens = login(new MockHttpSession());
        String refresh = JsonPath.read(tokens, "$.refresh_token");

        String renewed = mvc.perform(post("/oauth2/token").header(HttpHeaders.AUTHORIZATION, BASIC)
                        .param("grant_type", "refresh_token").param("refresh_token", refresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists())
                .andExpect(jsonPath("$.refresh_token").exists())
                .andReturn().getResponse().getContentAsString();

        String newRefresh = JsonPath.read(renewed, "$.refresh_token");
        assertThat(newRefresh).isNotEqualTo(refresh);

        // Token mới vẫn mang tenant và role: customizer chạy lại ở lần refresh
        String claims = payload(JsonPath.read(renewed, "$.access_token"));
        assertThat(claims).contains("\"roles\":[\"SUPER_ADMIN\"]").contains("\"tenant_id\"")
                .contains("\"aud\":[\"farm-service\",\"identity-service\"]");

        // Refresh token cũ đã bị thay: dùng lại bị từ chối
        mvc.perform(post("/oauth2/token").header(HttpHeaders.AUTHORIZATION, BASIC)
                        .param("grant_type", "refresh_token").param("refresh_token", refresh))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));

        // Refresh token mới dùng được
        mvc.perform(post("/oauth2/token").header(HttpHeaders.AUTHORIZATION, BASIC)
                        .param("grant_type", "refresh_token").param("refresh_token", newRefresh))
                .andExpect(status().isOk());
    }

    @Test
    void revokedRefreshTokenCannotBeUsed() throws Exception {
        String refresh = JsonPath.read(login(new MockHttpSession()), "$.refresh_token");

        mvc.perform(post("/oauth2/revoke").header(HttpHeaders.AUTHORIZATION, BASIC)
                        .param("token", refresh).param("token_type_hint", "refresh_token"))
                .andExpect(status().isOk());

        mvc.perform(post("/oauth2/token").header(HttpHeaders.AUTHORIZATION, BASIC)
                        .param("grant_type", "refresh_token").param("refresh_token", refresh))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void anotherClientCannotRevokeOrUseTheToken() throws Exception {
        String refresh = JsonPath.read(login(new MockHttpSession()), "$.refresh_token");
        String serviceBasic = "Basic " + Base64.getEncoder().encodeToString(
                "farmverse-service:test-service-secret-0123456789abcdef".getBytes());

        // client khác không đổi được refresh token của gateway
        mvc.perform(post("/oauth2/token").header(HttpHeaders.AUTHORIZATION, serviceBasic)
                        .param("grant_type", "refresh_token").param("refresh_token", refresh))
                .andExpect(status().is4xxClientError());

        // và revoke của client khác không làm token mất hiệu lực
        mvc.perform(post("/oauth2/revoke").header(HttpHeaders.AUTHORIZATION, serviceBasic)
                .param("token", refresh).param("token_type_hint", "refresh_token"));
        mvc.perform(post("/oauth2/token").header(HttpHeaders.AUTHORIZATION, BASIC)
                        .param("grant_type", "refresh_token").param("refresh_token", refresh))
                .andExpect(status().isOk());
    }

    @Test
    void rpInitiatedLogoutRedirectsBackAndEndsTheLoginSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String idToken = JsonPath.read(login(session), "$.id_token");

        mvc.perform(get(logoutUri(idToken, POST_LOGOUT, "bye")).session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(POST_LOGOUT + "?state=bye"));

        // Phiên đăng nhập của identity đã hết: authorize lại phải về trang login
        mvc.perform(get(authorizeUri()).session(session).header(HttpHeaders.ACCEPT, "text/html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void logoutToAnUnregisteredRedirectIsRejected() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String idToken = JsonPath.read(login(session), "$.id_token");

        mvc.perform(get(logoutUri(idToken, "http://evil.example/", null)).session(session))
                .andExpect(status().isBadRequest());
    }

    // ---- helpers ----

    /** Form login → /oauth2/authorize (PKCE) → /oauth2/token. Trả JSON token response. */
    private String login(MockHttpSession session) throws Exception {
        mvc.perform(post("/login").session(session).with(csrf())
                        .param("username", "admin@test.local")
                        .param("password", "Admin@123456789"))
                .andExpect(status().is3xxRedirection());

        String location = mvc.perform(get(authorizeUri()).session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        String code = UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("code");

        return mvc.perform(post("/oauth2/token").header(HttpHeaders.AUTHORIZATION, BASIC)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT)
                        .param("code_verifier", VERIFIER))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static String authorizeUri() {
        return "/oauth2/authorize?response_type=code&client_id=farmverse-gateway"
                + "&scope=openid profile email farmverse.api&state=xyz"
                + "&redirect_uri=" + REDIRECT
                + "&code_challenge=" + CHALLENGE + "&code_challenge_method=S256";
    }

    /** SAS đọc tham số logout từ query string (không đọc parameter map), nên dựng thẳng vào URL. */
    private static String logoutUri(String idToken, String postLogoutRedirect, String state) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromPath("/connect/logout")
                .queryParam("id_token_hint", idToken)
                .queryParam("client_id", "farmverse-gateway")
                .queryParam("post_logout_redirect_uri", postLogoutRedirect);
        if (state != null) {
            uri.queryParam("state", state);
        }
        return uri.build().toUriString();
    }

    private static String payload(String jwt) {
        return new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]));
    }
}
