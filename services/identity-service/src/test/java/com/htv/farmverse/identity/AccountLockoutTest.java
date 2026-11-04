package com.htv.farmverse.identity;

import java.util.Set;

import com.htv.farmverse.identity.tenant.Tenant;
import com.htv.farmverse.identity.tenant.TenantAdministrationService;
import com.htv.farmverse.identity.tenant.UserAdministrationService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * I3: khóa tạm tài khoản sau 5 lần sai mật khẩu ({@code farmverse.identity.lockout}), kể cả khi lần sau nhập đúng;
 * quản trị viên mở khóa được. Mỗi test dùng tài khoản và IP riêng để không vướng giới hạn theo IP hay test khác.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class AccountLockoutTest {

    static final String PASSWORD = "Password@12345";
    static final java.util.concurrent.atomic.AtomicInteger NEXT_ADDRESS = new java.util.concurrent.atomic.AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired TenantAdministrationService tenants;
    @Autowired UserAdministrationService users;

    String tenantId;
    String userId;
    String email;
    String address;

    @BeforeEach
    void user() {
        String code = "lock-" + System.nanoTime();
        Tenant tenant = tenants.create(code, "Lockout farm");
        tenantId = tenant.getId();
        email = code + "@test.local";
        userId = users.add(tenantId, email, "Lockout user", PASSWORD, Set.of("ADMIN")).userId();
        address = "10.9.2." + NEXT_ADDRESS.incrementAndGet();
    }

    private ResultActions login(String password) throws Exception {
        return mvc.perform(post("/login").with(csrf())
                .with(request -> { request.setRemoteAddr(address); return request; })
                .param("username", email).param("password", password));
    }

    @Test
    void fiveWrongPasswordsLockTheAccountEvenForTheRightPassword(CapturedOutput output) throws Exception {
        login(PASSWORD).andExpect(redirectedUrl("/"));          // đúng mật khẩu: vào được
        for (int i = 0; i < 5; i++) {
            login("wrong-password-" + i).andExpect(redirectedUrl("/login?error"));
        }

        login(PASSWORD).andExpect(redirectedUrl("/login?error"));

        assertThat(users.get(tenantId, userId).locked()).isTrue();
        assertThat(output.getAll()).contains("account.locked").doesNotContain(PASSWORD);
    }

    @Test
    void adminUnlockLetsTheUserBackIn() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("wrong-password-" + i);
        }
        login(PASSWORD).andExpect(redirectedUrl("/login?error"));

        users.unlock(tenantId, userId);

        login(PASSWORD).andExpect(redirectedUrl("/"));
    }

    @Test
    void successfulLoginResetsTheFailureCount() throws Exception {
        for (int i = 0; i < 4; i++) {
            login("wrong-password-" + i);
        }
        login(PASSWORD).andExpect(redirectedUrl("/"));
        for (int i = 0; i < 4; i++) {
            login("wrong-password-again-" + i);
        }

        login(PASSWORD).andExpect(redirectedUrl("/"));
    }
}
