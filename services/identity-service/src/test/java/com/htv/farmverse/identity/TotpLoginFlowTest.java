package com.htv.farmverse.identity;

import java.time.Instant;
import java.util.Set;

import com.htv.farmverse.identity.account.UserAccountRepository;
import com.htv.farmverse.identity.mfa.TotpCodes;
import com.htv.farmverse.identity.mfa.TotpService;
import com.htv.farmverse.identity.tenant.Tenant;
import com.htv.farmverse.identity.tenant.TenantAdministrationService;
import com.htv.farmverse.identity.tenant.UserAdministrationService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tài khoản bật TOTP: mật khẩu đúng chưa đủ, phải qua /login/totp mới được cấp code. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TotpLoginFlowTest {

    static final String AUTHORIZE = "/oauth2/authorize?response_type=code&client_id=farmverse-gateway&scope=openid"
            + "&redirect_uri=http://127.0.0.1:8080/login/oauth2/code/farmverse"
            + "&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256";

    @Autowired MockMvc mvc;
    @Autowired TenantAdministrationService tenants;
    @Autowired UserAdministrationService users;
    @Autowired UserAccountRepository accounts;
    @Autowired TotpService totp;

    String secret;
    String userId;

    @BeforeEach
    void userWithTotp() {
        String code = "mfa-" + System.nanoTime();
        Tenant tenant = tenants.create(code, "MFA farm");
        userId = users.add(tenant.getId(), code + "@test.local", "MFA user", "Password@12345", Set.of("ADMIN")).userId();
        secret = totp.begin(userId).secret();
        long step = TotpCodes.stepAt(Instant.now());
        totp.confirm(userId, TotpCodes.codeAt(secret, step - 1)); // dùng bước trước, để bước hiện tại cho đăng nhập
    }

    @Test
    void passwordAloneIsNotEnoughThenTotpCompletesAuthorization() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String email = accounts.findById(userId).orElseThrow().getEmail();

        mvc.perform(post("/login").session(session).with(csrf())
                        .param("username", email).param("password", "Password@12345"))
                .andExpect(status().is3xxRedirection());

        mvc.perform(get(AUTHORIZE).session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login/totp"));

        mvc.perform(post("/login/totp").session(session).with(csrf()).param("code", "000000"))
                .andExpect(redirectedUrl("/login/totp?error"));

        String back = mvc.perform(post("/login/totp").session(session).with(csrf())
                        .param("code", TotpCodes.codeAt(secret, TotpCodes.stepAt(Instant.now()))))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(back).contains("/oauth2/authorize");

        String location = mvc.perform(get(AUTHORIZE).session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(location).startsWith("http://127.0.0.1:8080/login/oauth2/code/farmverse?code=");
    }

    @Test
    void sameCodeCannotBeUsedTwice() {
        String now = TotpCodes.codeAt(secret, TotpCodes.stepAt(Instant.now()));
        assertThat(totp.verify(userId, now)).isTrue();
        assertThat(totp.verify(userId, now)).isFalse();
    }
}
