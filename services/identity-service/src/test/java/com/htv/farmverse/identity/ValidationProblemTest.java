package com.htv.farmverse.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Lỗi 400 của REST quản trị: ProblemDetail có code SF-VALIDATION-001, liệt kê field sai, không lộ giá trị đã gửi. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ValidationProblemTest {

    @Autowired
    MockMvc mvc;

    @Test
    void invalidBodyReturnsProblemWithFieldErrorsButNotTheSubmittedValues() throws Exception {
        mvc.perform(post("/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("identity:user:manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"displayName\":\"\",\"password\":\"secret-pass\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("SF-VALIDATION-001"))
                .andExpect(jsonPath("$.type").value("urn:farmverse:problem:SF-VALIDATION-001"))
                .andExpect(jsonPath("$.errors[?(@.field=='email')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='displayName')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='password')]").exists())
                .andExpect(content().string(not(containsString("not-an-email"))))
                .andExpect(content().string(not(containsString("secret-pass"))));
    }

    @Test
    void malformedJsonReturnsProblemWithHttpCode() throws Exception {
        mvc.perform(post("/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("identity:user:manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("SF-HTTP-400"));
    }
}
