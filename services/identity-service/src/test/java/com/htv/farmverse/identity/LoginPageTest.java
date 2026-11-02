package com.htv.farmverse.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Giao diện trang đăng nhập: có nhãn, có CSS (tải được khi chưa đăng nhập), hiện đúng thông báo, không chèn được HTML. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginPageTest {

    @Autowired
    MockMvc mvc;

    @Test
    void formHasLabelsAndLinksTheSharedStylesheet() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/css/auth.css\"")))
                .andExpect(content().string(containsString("<label for=\"username\"")))
                .andExpect(content().string(containsString("<label for=\"password\"")))
                .andExpect(content().string(containsString("autocomplete=\"current-password\"")))
                .andExpect(content().string(not(containsString("role=\"alert\""))));
    }

    @Test
    void stylesheetIsPublic() throws Exception {
        mvc.perform(get("/css/auth.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("prefers-color-scheme")));
    }

    @Test
    void wrongPasswordShowsAnAlertThatTheFieldsPointTo() throws Exception {
        mvc.perform(get("/login").param("error", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("role=\"alert\"")))
                .andExpect(content().string(containsString("Email hoặc mật khẩu không đúng")))
                .andExpect(content().string(containsString("aria-describedby=\"login-message\"")));
    }

    @Test
    void rateLimitMessageShowsOnlyASafeNumberOfSeconds() throws Exception {
        mvc.perform(get("/login").param("limited", "45"))
                .andExpect(content().string(containsString("khoảng 45 giây")));

        // Giá trị do người ngoài đặt trên URL không bao giờ được in ra
        mvc.perform(get("/login").param("limited", "<script>alert(1)</script>"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<script>alert"))))
                .andExpect(content().string(containsString("đợi một lúc")));
    }
}
