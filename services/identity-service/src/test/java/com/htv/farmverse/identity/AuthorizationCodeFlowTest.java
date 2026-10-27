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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Luồng thật mà gateway (BFF) sẽ đi: form login → /oauth2/authorize (PKCE) → /oauth2/token,
 * rồi dùng access token gọi REST quản trị.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthorizationCodeFlowTest {

    static final String REDIRECT = "http://127.0.0.1:8080/login/oauth2/code/farmverse";
    // Cặp mẫu trong RFC 7636 (code_verifier → S256 code_challenge)
    static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    @Autowired
    MockMvc mvc;

    @Test
    void superAdminGetsTokenWithTenantAndRolesThenUsesAdminApi() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/login").session(session).with(csrf())
                        .param("username", "admin@test.local")
                        .param("password", "Admin@123456789"))
                .andExpect(status().is3xxRedirection());

        String location = mvc.perform(get("/oauth2/authorize?response_type=code&client_id=farmverse-gateway"
                        + "&scope=openid profile email farmverse.api&state=xyz"
                        + "&redirect_uri=" + "http://127.0.0.1:8080/login/oauth2/code/farmverse"
                        + "&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256").session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(location).startsWith(REDIRECT);
        String code = UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("code");

        String basic = Base64.getEncoder().encodeToString(
                "farmverse-gateway:test-gateway-secret-0123456789abcdef".getBytes());
        String tokens = mvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT)
                        .param("code_verifier", VERIFIER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refresh_token").exists())
                .andExpect(jsonPath("$.id_token").exists())
                .andReturn().getResponse().getContentAsString();

        String accessToken = JsonPath.read(tokens, "$.access_token");
        String claims = new String(Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]));
        assertThat(claims)
                .contains("\"aud\":\"farmverse-api\"")
                .contains("\"token_type\":\"user\"")
                .contains("\"roles\":[\"SUPER_ADMIN\"]")
                .contains("\"tenant_id\"")
                .contains("\"membership_id\"");

        mvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@test.local"))
                .andExpect(jsonPath("$.roles[0]").value("SUPER_ADMIN"));

        mvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("admin@test.local"));
    }

    @Test
    void wrongPasswordStaysOnLoginPage() throws Exception {
        mvc.perform(post("/login").with(csrf())
                        .param("username", "admin@test.local")
                        .param("password", "wrong-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .redirectedUrl("/login?error"));
    }
}
