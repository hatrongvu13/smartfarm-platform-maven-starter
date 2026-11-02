package com.htv.farmverse.service.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;

class ProblemResponsesTest {

    @Test
    void unauthorizedKeepsBearerHeaderAndWritesProblemJson() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ProblemResponses.unauthorized().commence(new MockHttpServletRequest(), response, new BadCredentialsException("x"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).startsWith("Bearer");
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"status\":401").contains("\"code\":\"SF-AUTH-001\"");
    }

    @Test
    void forbiddenWritesProblemJson() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ProblemResponses.forbidden().handle(new MockHttpServletRequest(), response, new AccessDeniedException("x"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"code\":\"SF-AUTH-002\"");
    }
}
