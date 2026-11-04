package com.htv.farmverse.gateway.config;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimitFilterTest {

    /** Bộ đếm trong bộ nhớ (không cửa sổ thời gian): đủ để kiểm logic của filter. */
    static final class Counter implements LoginAttemptCounter {
        final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
        final List<String> keys = new ArrayList<>();

        @Override
        public Mono<Long> increment(String key, Duration window) {
            keys.add(key);
            return Mono.just((long) counts.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet());
        }
    }

    private final Counter counter = new Counter();
    private final LoginRateLimitFilter filter = new LoginRateLimitFilter(counter, 2, Duration.ofMinutes(1));

    private MockServerWebExchange call(String path, String ip) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path)
                .remoteAddress(new InetSocketAddress(ip, 5000)));
        StepVerifier.create(filter.filter(exchange, ex -> {
            ex.getResponse().setStatusCode(HttpStatus.FOUND);
            return ex.getResponse().setComplete();
        })).verifyComplete();
        return exchange;
    }

    @Test
    void blocksTheThirdAttemptFromTheSameIp() {
        assertThat(call("/oauth2/authorization/farmverse", "198.51.100.7").getResponse().getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(call("/login/oauth2/code/farmverse", "198.51.100.7").getResponse().getStatusCode()).isEqualTo(HttpStatus.FOUND);

        MockServerWebExchange third = call("/oauth2/authorization/farmverse", "198.51.100.7");
        assertThat(third.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(third.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(third.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("60");
        assertThat(third.getResponse().getBodyAsString().block()).contains("\"code\":\"SF-GATEWAY-429\"");
    }

    @Test
    void eachIpHasItsOwnBudget() {
        call("/oauth2/authorization/farmverse", "198.51.100.7");
        call("/oauth2/authorization/farmverse", "198.51.100.7");
        assertThat(call("/oauth2/authorization/farmverse", "203.0.113.9").getResponse().getStatusCode())
                .isEqualTo(HttpStatus.FOUND);
    }

    @Test
    void otherPathsAreNotCountedOrLimited() {
        for (int i = 0; i < 5; i++) {
            assertThat(call("/api/farm/farms", "198.51.100.7").getResponse().getStatusCode()).isEqualTo(HttpStatus.FOUND);
            call("/", "198.51.100.7");
            call("/oauth2/authorizationX", "198.51.100.7");
        }
        assertThat(counter.keys).isEmpty();
    }

    @Test
    void keyIsTheIpOnlyWithoutQueryOrHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/login/oauth2/code/farmverse?code=secret-code&state=s")
                .remoteAddress(new InetSocketAddress("2001:db8::1", 5000))
                .header("X-Forwarded-For", "10.9.9.9"));
        StepVerifier.create(filter.filter(exchange, ex -> ex.getResponse().setComplete())).verifyComplete();

        assertThat(counter.keys).containsExactly("2001:db8:0:0:0:0:0:1");
    }
}
