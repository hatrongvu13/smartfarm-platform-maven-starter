package com.htv.farmverse.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import com.htv.farmverse.identity.login.LoginRateLimitFilter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LoginRateLimitFilterTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final Clock clock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(now.get());
        }
    };
    private final LoginRateLimitFilter filter = new LoginRateLimitFilter(3, Duration.ofMinutes(1), clock);

    private MockHttpServletResponse post(String path, String address) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(address);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void blocksTheFourthAttemptFromTheSameAddressWithProblemJson() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(post("/login", "10.0.0.1").getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse blocked = post("/login", "10.0.0.1");

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
        assertThat(blocked.getContentType()).startsWith("application/problem+json");
        assertThat(blocked.getContentAsString()).contains("\"code\":\"SF-AUTH-003\"");
    }

    @Test
    void otherAddressesAndOtherPathsAreNotAffected() throws Exception {
        for (int i = 0; i < 4; i++) {
            post("/login", "10.0.0.1");
        }

        assertThat(post("/login", "10.0.0.2").getStatus()).isEqualTo(200);
        assertThat(filterGet("/login", "10.0.0.1").getStatus()).isEqualTo(200);
    }

    @Test
    void allowsAgainAfterTheWindowEnds() throws Exception {
        for (int i = 0; i < 4; i++) {
            post("/login/totp", "10.0.0.1");
        }
        assertThat(post("/login/totp", "10.0.0.1").getStatus()).isEqualTo(429);

        now.addAndGet(Duration.ofMinutes(1).toMillis());

        assertThat(post("/login/totp", "10.0.0.1").getStatus()).isEqualTo(200);
    }

    @Test
    void browserFormIsRedirectedBackToThePageWithTheWaitTimeInsteadOfRawJson() throws Exception {
        for (int i = 0; i < 3; i++) {
            post("/login", "10.0.0.1");
        }
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login/totp");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        for (int i = 0; i < 3; i++) {
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        }
        request.addHeader("Accept", "text/html,application/xhtml+xml");
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(303);
        assertThat(response.getHeader("Location")).isEqualTo("/login/totp?limited=60");
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(response.getContentAsString()).isEmpty();
    }

    private MockHttpServletResponse filterGet(String path, String address) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRemoteAddr(address);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
