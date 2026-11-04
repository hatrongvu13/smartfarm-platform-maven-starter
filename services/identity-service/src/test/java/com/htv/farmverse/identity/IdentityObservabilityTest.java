package com.htv.farmverse.identity;

import java.util.Base64;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M7, T16 ở identity: chạy trọn luồng đăng nhập → mã → token → gọi API với log DEBUG của security và web,
 * rồi kiểm không bí mật nào xuất hiện trong log: mật khẩu, client secret, mã OAuth2, code_verifier,
 * access/refresh token. Log mang trace id nhận từ gateway. Metric Prometheus không mở ở cổng công khai.
 */
@SpringBootTest(properties = {
        "logging.level.org.springframework.security=DEBUG",
        "logging.level.org.springframework.web=DEBUG"
})
@AutoConfigureMockMvc
@AutoConfigureMetrics
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class IdentityObservabilityTest {

    static final String REDIRECT = "http://127.0.0.1:8080/login/oauth2/code/farmverse";
    static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
    static final String PASSWORD = "Admin@123456789";
    static final String CLIENT_SECRET = "test-gateway-secret-0123456789abcdef";
    static final String TRACE_ID = "5b8efff798038103d269b633813fc60c";

    @Autowired MockMvc mvc;

    @Test
    void loginAndTokenFlowLeavesNoSecretsInTheLogs(CapturedOutput output) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/login").session(session).with(csrf())
                        .param("username", "admin@test.local").param("password", PASSWORD))
                .andExpect(status().is3xxRedirection());

        String location = mvc.perform(get("/oauth2/authorize?response_type=code&client_id=farmverse-gateway"
                        + "&scope=openid farmverse.api&state=xyz&redirect_uri=" + REDIRECT
                        + "&code_challenge=" + CHALLENGE + "&code_challenge_method=S256").session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        String code = UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("code");

        String basic = Base64.getEncoder().encodeToString(("farmverse-gateway:" + CLIENT_SECRET).getBytes());
        String tokens = mvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                        .param("grant_type", "authorization_code").param("code", code)
                        .param("redirect_uri", REDIRECT).param("code_verifier", VERIFIER))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(tokens, "$.access_token");
        String refreshToken = JsonPath.read(tokens, "$.refresh_token");

        mvc.perform(get("/me")
                        .header("traceparent", "00-" + TRACE_ID + "-53995c3f42cd8ad8-01")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        assertThat(output.getAll())
                .contains(" DEBUG ")
                .contains(TRACE_ID)
                .doesNotContain(PASSWORD)
                .doesNotContain(CLIENT_SECRET)
                .doesNotContain(basic)
                .doesNotContain(code)
                .doesNotContain(VERIFIER)
                .doesNotContain(accessToken)
                .doesNotContain(accessToken.split("\\.")[2])     // chữ ký riêng, phòng trường hợp token bị cắt
                .doesNotContain(refreshToken);
    }

    @Test
    void prometheusIsNotOnThePublicPort() throws Exception {
        mvc.perform(get("/actuator/prometheus")).andExpect(result ->
                assertThat(result.getResponse().getContentAsString()).doesNotContain("jvm_"));
    }
}
