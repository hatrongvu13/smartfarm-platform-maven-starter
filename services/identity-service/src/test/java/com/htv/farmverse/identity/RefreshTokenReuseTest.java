package com.htv.farmverse.identity;

import java.util.Base64;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * I1, RFC 9700 §4.14.2: dùng lại refresh token đã bị xoay thì thu hồi cả "họ" token (authorization).
 * {@code refresh-token-reuse-grace=0s} để mọi lần dùng lại đều bị coi là đánh cắp. Hành vi trong khoảng grace
 * (mặc định 30 giây: token cũ bị từ chối, token mới vẫn chạy) đã có ở {@link TokenLifecycleTest}.
 */
@SpringBootTest(properties = "farmverse.identity.refresh-token-reuse-grace=0s")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class RefreshTokenReuseTest {

    static final String REDIRECT = "http://127.0.0.1:8080/login/oauth2/code/farmverse";
    static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
    static final String BASIC = "Basic " + Base64.getEncoder().encodeToString(
            "farmverse-gateway:test-gateway-secret-0123456789abcdef".getBytes());

    @Autowired
    MockMvc mvc;

    @Test
    void reusingARotatedRefreshTokenRevokesTheWholeFamily(CapturedOutput output) throws Exception {
        String first = JsonPath.read(login(), "$.refresh_token");
        String second = refresh(first);                    // người dùng thật refresh: first bị xoay

        // Kẻ trộm dùng token cũ: bị từ chối...
        mvc.perform(post("/oauth2/token").header(HttpHeaders.AUTHORIZATION, BASIC)
                        .param("grant_type", "refresh_token").param("refresh_token", first))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));

        // ...và token mới nhất cũng mất hiệu lực: phải đăng nhập lại
        mvc.perform(post("/oauth2/token").header(HttpHeaders.AUTHORIZATION, BASIC)
                        .param("grant_type", "refresh_token").param("refresh_token", second))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));

        assertThat(output.getAll())
                .contains("token.refresh_reuse_detected")
                .doesNotContain(first)
                .doesNotContain(second);
    }

    /** Họ token khác (một lần đăng nhập khác) không bị ảnh hưởng. */
    @Test
    void otherLoginsKeepWorking() throws Exception {
        String victim = JsonPath.read(login(), "$.refresh_token");
        String bystander = JsonPath.read(login(), "$.refresh_token");
        refresh(victim);
        mvc.perform(post("/oauth2/token").header(HttpHeaders.AUTHORIZATION, BASIC)
                .param("grant_type", "refresh_token").param("refresh_token", victim));

        refresh(bystander);
    }

    private String refresh(String refreshToken) throws Exception {
        String body = mvc.perform(post("/oauth2/token").header(HttpHeaders.AUTHORIZATION, BASIC)
                        .param("grant_type", "refresh_token").param("refresh_token", refreshToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.refresh_token");
    }

    private String login() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/login").session(session).with(csrf())
                        .with(request -> { request.setRemoteAddr("10.9.1.1"); return request; })
                        .param("username", "admin@test.local").param("password", "Admin@123456789"))
                .andExpect(status().is3xxRedirection());
        String location = mvc.perform(get("/oauth2/authorize?response_type=code&client_id=farmverse-gateway"
                        + "&scope=openid farmverse.api&state=s&redirect_uri=" + REDIRECT
                        + "&code_challenge=" + CHALLENGE + "&code_challenge_method=S256").session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        String code = UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("code");
        return mvc.perform(post("/oauth2/token").header(HttpHeaders.AUTHORIZATION, BASIC)
                        .param("grant_type", "authorization_code").param("code", code)
                        .param("redirect_uri", REDIRECT).param("code_verifier", VERIFIER))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
