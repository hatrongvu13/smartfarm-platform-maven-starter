package com.htv.farmverse.identity;

import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Endpoint chuẩn của SAS: discovery, JWKS, client_credentials, và REST quản trị chặn đúng. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthorizationServerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void publishesOidcDiscoveryAndJwks() throws Exception {
        mvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("http://localhost:8092"))
                .andExpect(jsonPath("$.jwks_uri").value("http://localhost:8092/oauth2/jwks"));
        mvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kid").value("test-key"));
    }

    @Test
    void clientCredentialsTokenIsServiceTokenForApiAudience() throws Exception {
        String basic = Base64.getEncoder().encodeToString(
                "farmverse-service:test-service-secret-0123456789abcdef".getBytes());
        String body = mvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                        .param("grant_type", "client_credentials")
                        .param("scope", "farmverse.api"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(body, "$.access_token");
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        org.assertj.core.api.Assertions.assertThat(payload)
                .contains("\"aud\":\"farmverse-api\"")
                .contains("\"token_type\":\"service\"");

        // token service không có perms → không vào được REST quản trị
        mvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminApiRequiresBearerToken() throws Exception {
        mvc.perform(get("/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void browserIsSentToLoginPage() throws Exception {
        mvc.perform(get("/oauth2/authorize?response_type=code&client_id=farmverse-gateway"
                        + "&scope=openid"
                        + "&redirect_uri=" + "http://127.0.0.1:8080/login/oauth2/code/farmverse"
                        + "&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256")
                        .header(HttpHeaders.ACCEPT, "text/html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .redirectedUrl("/login"));
    }
}
